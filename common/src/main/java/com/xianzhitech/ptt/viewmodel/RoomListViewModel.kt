package com.xianzhitech.ptt.viewmodel

import android.databinding.ObservableArrayMap
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.data.MessageWithSender
import com.xianzhitech.ptt.data.RoomInfo
import com.xianzhitech.ptt.ext.combineLatest
import com.xianzhitech.ptt.ext.doOnLoading
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.util.ObservableArrayList
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*
import java.util.concurrent.TimeUnit


class RoomListViewModel(private val appComponent: AppComponent,
                        private val navigator : Navigator) : LifecycleViewModel() {
    val roomViewModels = ObservableArrayList<RoomItemViewModel>()
    val roomLatestMessages = ObservableArrayMap<String, MessageWithSender>()
    val roomUnreadMessageCount = ObservableArrayMap<String, Int>()
    val roomInfo = ObservableArrayMap<String, RoomInfo>()
    val emptyViewVisible = ObservableBoolean()
    val latestMessageSyncDate = ObservableField<Date>()
    val isLoading = ObservableBoolean()

    override fun onStart() {
        super.onStart()

        latestMessageSyncDate.set(appComponent.preference.lastMessageSyncDate)

        combineLatest(
                appComponent.storage.getAllRoomLatestMessage(),
                appComponent.storage.getAllRooms(),
                { messages, rooms -> messages to rooms })
                .publish {
                    it.take(1).mergeWith(it.debounce(200, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())).distinctUntilChanged()
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (messages, rooms) ->
                    val sortedRooms = rooms.sortedByDescending { messages[it.room.id]?.message?.sendTime }

                    roomViewModels.replaceAll(sortedRooms.mapNotNull { room ->
                        if (room.name.isBlank()) {
                            null
                        } else {
                            RoomItemViewModel(appComponent, room, roomLatestMessages, roomUnreadMessageCount, navigator)
                        }
                    })

                    roomLatestMessages.clear()
                    roomLatestMessages.putAll(messages)

                    emptyViewVisible.set(roomViewModels.isEmpty())
                }
                .bindToLifecycle()

        appComponent.storage.getAllRoomUnreadMessageCount()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    roomUnreadMessageCount.clear()
                    roomUnreadMessageCount.putAll(it)
                }

        appComponent.storage.getAllRoomInfo()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    roomInfo.clear()
                    roomInfo.putAll(it.associateBy(RoomInfo::roomId))
                }
                .bindToLifecycle()
    }

    fun onCreateRoomMemberSelectionResult(userIds: List<String>) {
        appComponent.signalBroker
                .createRoom(userIds = userIds)
                .doOnLoading(isLoading::set)
                .observeOn(AndroidSchedulers.mainThread())
                .toMaybe()
                .logErrorAndForget(Throwable::toast)
                .subscribe(navigator::navigateToRoom)
    }

    interface Navigator : RoomItemViewModel.Navigator
}