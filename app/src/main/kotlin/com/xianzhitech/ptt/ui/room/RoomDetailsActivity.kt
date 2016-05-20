package com.xianzhitech.ptt.ui.room

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.wefika.flowlayout.FlowLayout
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import rx.Observable

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

        Observable.combineLatest(
                appComponent.roomRepository.getRoom(roomId).observe().map { it ?: throw StaticUserException(R.string.error_room_not_exists) },
                appComponent.roomRepository.getRoomName(roomId).observe(),
                appComponent.roomRepository.getRoomMembers(roomId, maxMemberCount = MAX_MEMBER_DISPLAY_COUNT + 1).observe(),
                { room, name, members ->
                    RoomData(room, name, members.subList(0, MAX_MEMBER_DISPLAY_COUNT), members.size >= MAX_MEMBER_DISPLAY_COUNT)
                }
        )
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe(object : GlobalSubscriber<RoomData>(this) {
                    override fun onError(e: Throwable) {
                        super.onError(e)
                        finish()
                    }

                    override fun onNext(t: RoomData) {
                        onRoomLoaded(t.room, t.name, t.members, t.hasMore)
                    }
                })
    }

    private fun onRoomLoaded(room: Room, roomName : RoomName, roomMembers: List<User>, hasMore : Boolean) {
        roomNameView.text = roomName.name

        val items = arrayListOf<UserItemHolder>()

        // Collect holders from the flow view
        for (i in 0..memberView.childCount-1) {
            val tag = memberView.getChildAt(i).tag
            if (tag is UserItemHolder) {
                items.add(tag)
            }
        }
        memberView.removeAllViews()

        // Create holders for new member
        val createSize = roomMembers.size - items.size
        val inflater = LayoutInflater.from(this)
        for (i in 0..createSize-1) {
            items.add(UserItemHolder(inflater.inflate(R.layout.view_room_member_item, memberView, false)))
        }

        // Set up holders
        roomMembers.forEachIndexed { i, member ->
            items[i].iconView.setImageDrawable(member.createAvatarDrawable(this))
            items[i].nameView.text = member.name
            memberView.addView(items[i].rootView)
        }
    }

    private class UserItemHolder(val rootView : View,
                                 val iconView : ImageView = rootView.findView(R.id.roomMemberItem_icon),
                                 val nameView : TextView = rootView.findView(R.id.roomMemberItem_name)) {
        init {
            rootView.tag = this
        }
    }

    private data class RoomData(val room: Room,
                                val name: RoomName,
                                val members: List<User>,
                                val hasMore : Boolean)

    companion object {
        const val EXTRA_ROOM_ID = "room_id"

        const val MAX_MEMBER_DISPLAY_COUNT = 20
    }
}