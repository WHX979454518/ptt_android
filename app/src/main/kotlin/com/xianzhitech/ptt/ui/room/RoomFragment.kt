package com.xianzhitech.ptt.ui.room

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.service.currentRoomId
import com.xianzhitech.ptt.service.currentUserId
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import rx.Observable

class RoomFragment : BaseFragment()
        , BackPressable
        , AlertDialogFragment.OnPositiveButtonClickListener
        , AlertDialogFragment.OnNegativeButtonClickListener
        , AlertDialogFragment.OnNeutralButtonClickListener {

    private class Views(rootView: View,
                        val titleView : TextView = rootView.findView(R.id.room_title))

    private var views: Views? = null
    private lateinit var appComponent : AppComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appComponent = (activity.application as AppComponent)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_room, container, false)
        val views = Views(rootView)
        rootView.findViewById(R.id.room_leave)?.setOnClickListener {
            appComponent.signalService.leaveRoom().subscribeSimple()
            activity.finish()
        }

        rootView.findViewById(R.id.room_info)?.setOnClickListener {
            val roomId = appComponent.signalService.currentRoomId
            if (roomId != null) {
                activity.startActivityWithAnimation(RoomDetailsActivity.build(context, roomId))
            }
        }

        this.views = views;
        return rootView
    }

    override fun onStart() {
        super.onStart()

        val signalService = appComponent.signalService
        val stateByRoomId = signalService.roomState.distinctUntilChanged { it.currentRoomId }

        Observable.combineLatest(
                stateByRoomId.switchMap { appComponent.roomRepository.getRoomName(it.currentRoomId, excludeUserIds = arrayOf(signalService.currentUserId)).observe() },
                signalService.roomState.distinctUntilChanged { it.onlineMemberIds }.map { it.onlineMemberIds },
                stateByRoomId.switchMap { appComponent.roomRepository.getRoomMembers(it.currentRoomId).observe() },
                stateByRoomId.switchMap { appComponent.roomRepository.getRoom(it.currentRoomId).observe() },
                { roomName, onlineMemberIds, roomMembers, room -> RoomInfo(roomName, room, roomMembers, onlineMemberIds) }
        )
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    if (it.roomName != null && it.room != null) {
                        callbacks<Callbacks>()?.onRoomLoaded(it.roomName.name)
                        views?.titleView?.text = R.string.room_name_with_numbers.toFormattedString(context, it.roomName.name, it.onlineMemberIds.size, it.roomMembers.size)
                    }
                }
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) { }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) { }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) { }

    interface Callbacks {
        fun onRoomLoaded(name: CharSequence)
        fun onRoomQuited()
    }

    private data class RoomInfo(val roomName: RoomName?,
                                val room: Room?,
                                val roomMembers: List<User>,
                                val onlineMemberIds: Collection<String>)

    companion object {

    }
}