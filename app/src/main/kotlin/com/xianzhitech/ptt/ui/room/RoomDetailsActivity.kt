package com.xianzhitech.ptt.ui.room

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.wefika.flowlayout.FlowLayout
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity

class RoomDetailsActivity : BaseToolbarActivity() {
    private lateinit var memberView : FlowLayout
    private lateinit var allMemberLabelView : TextView
    private lateinit var roomNameView : TextView
    private lateinit var deleteRoomButton : View

    private lateinit var appComponent : AppComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_room_details)

        memberView = findView(R.id.roomDetails_members)
        allMemberLabelView = findView(R.id.roomDetails_allMemberLabel)
        roomNameView = findView(R.id.roomDetails_name)
        deleteRoomButton = findView(R.id.roomDetails_deleteRoom)
        appComponent = application as AppComponent

        title = R.string.room_info.toFormattedString(this)
    }

    override fun onStart() {
        super.onStart()

        val roomId : String = intent.getStringExtra(EXTRA_ROOM_ID)

        appComponent.signalService.retrieveRoomInfo(roomId)
                .subscribeSimple {
                    appComponent.roomRepository.saveRooms(listOf(it)).execAsync().subscribeSimple()
                }

        appComponent.roomRepository.getRoom(roomId)
                .observe()
                .map { it ?: throw StaticUserException(R.string.error_room_not_exists) }
                .distinctUntilChanged()
                .combineWith(appComponent.roomRepository.getRoomName(roomId, excludeUserIds = arrayOf(appComponent.signalService.peekLoginState().currentUserID)).observe())
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe(object : GlobalSubscriber<Pair<Room, RoomName>>(this) {
                    override fun onError(e: Throwable) {
                        super.onError(e)
                        finish()
                    }

                    override fun onNext(t: Pair<Room, RoomName>) {
                        onRoomLoaded(t.first, t.second)
                    }
                })
    }

    private fun onRoomLoaded(room: Room, roomName : RoomName) {
        roomNameView.text = roomName.name

    }

    companion object {
        const val EXTRA_ROOM_ID = "room_id"

        const val MAX_MEMBER_DISPLAY_COUNT = 20
    }
}