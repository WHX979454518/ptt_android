package com.xianzhitech.ptt.ui.room

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.wefika.flowlayout.FlowLayout
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.createAvatarDrawable
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.ext.startActivityForResultWithAnimation
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.service.currentUserId
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import com.xianzhitech.ptt.ui.user.ContactUserProvider
import com.xianzhitech.ptt.ui.user.UserListActivity
import rx.Completable
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

class RoomDetailsActivity : BaseToolbarActivity() {
    private lateinit var memberView : FlowLayout
    private lateinit var allMemberLabelView : TextView
    private lateinit var roomNameView : TextView
    private lateinit var deleteRoomButton : View

    private lateinit var appComponent : AppComponent

    private val roomId : String
        get() = intent.getStringExtra(EXTRA_ROOM_ID)

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

        appComponent.signalService.retrieveRoomInfo(roomId)
                .subscribeSimple {
                    appComponent.roomRepository.saveRooms(listOf(it)).execAsync().subscribeSimple()
                }

        Observable.combineLatest(
                appComponent.roomRepository.getRoom(roomId).observe().map { it ?: throw StaticUserException(R.string.error_room_not_exists) },
                appComponent.roomRepository.getRoomName(roomId, excludeUserIds = arrayOf(appComponent.signalService.peekLoginState().currentUserID)).observe(),
                appComponent.roomRepository.getRoomMembers(roomId, maxMemberCount = Int.MAX_VALUE).observe(),
                { room, name, members ->
                    RoomData(room, name, members)
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
                        onRoomLoaded(t.room, t.name, t.members)
                    }
                })
    }

    private fun onRoomLoaded(room: Room, roomName : RoomName, roomMembers: List<User>) {
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
        val displayMemberCount = Math.min(MAX_MEMBER_DISPLAY_COUNT, roomMembers.size)
        val createSize = displayMemberCount - items.size + 1 // Extra one for 'add' button
        val inflater = LayoutInflater.from(this)
        for (i in 0..createSize-1) {
            items.add(UserItemHolder(inflater.inflate(R.layout.view_room_member_item, memberView, false)))
        }

        // Set up holders
        roomMembers.subList(0, displayMemberCount).forEachIndexed { i, member ->
            items[i].setUser(member)
            memberView.addView(items[i].rootView)
        }

        // Add member button
        val addButton = items.last()
        addButton.nameView.text = " "
        getTintedDrawable(R.drawable.ic_person_add_24dp, addButton.nameView.currentTextColor).let {
            it.setBounds(0, 0, addButton.iconSize, addButton.iconSize)
            addButton.nameView.setCompoundDrawables(null, it, null, null)
        }
        addButton.rootView.setOnClickListener {
            startActivityForResultWithAnimation(
                    UserListActivity.build(this, R.string.add_members.toFormattedString(this),
                            ContactUserProvider(), true, null,
                            roomMembers.map { it.id }, false),
                    REQUEST_SELECT_USER
            )
        }
        memberView.addView(addButton.rootView)


        // Setup label
        allMemberLabelView.text = R.string.room_all_member_with_number.toFormattedString(this, roomMembers.size)

        // Setup delete button
        deleteRoomButton.setVisible(room.ownerId == appComponent.signalService.currentUserId)


        // Set up all member views
        allMemberLabelView.setOnClickListener {
            startActivityWithAnimation(
                    UserListActivity.build(this, R.string.room_members.toFormattedString(this),
                            RoomMemberProvider(room.id), false, null,
                            emptyList(), false)
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_SELECT_USER && resultCode == RESULT_OK && data != null) {
            val selectedUserIds = data.getStringArrayExtra(UserListActivity.RESULT_EXTRA_SELECTED_USER_IDS)
            (application as AppComponent).signalService.updateRoomMembers(roomId, selectedUserIds.toList())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(RoomUpdateSubscriber(this))
        }
        else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private class RoomUpdateSubscriber(private val context: Context) : Completable.CompletableSubscriber {
        override fun onSubscribe(d: Subscription?) { }

        override fun onError(e: Throwable?) {
            Log.e("RoomDetailsActivity", "Error updating room: ", e)
            Toast.makeText(context, e.describeInHumanMessage(context), Toast.LENGTH_LONG).show()
        }

        override fun onCompleted() {
            Toast.makeText(context, R.string.room_updated.toFormattedString(context), Toast.LENGTH_LONG).show()
        }
    }

    private class UserItemHolder(val rootView : View,
                                 val nameView : TextView = rootView.findView(R.id.roomMemberItem_name)) {

        val iconSize = rootView.resources.getDimensionPixelSize(R.dimen.room_details_member_icon_size)
        private var user : User? = null

        init {
            rootView.tag = this
        }

        fun setUser(user : User) {
            if (this.user?.id != user.id) {
                val drawable = user.createAvatarDrawable(rootView.context)
                drawable.setBounds(0, 0, iconSize, iconSize)
                nameView.setCompoundDrawables(null, drawable, null, null)
                nameView.text = user.name
            }
        }
    }

    private data class RoomData(val room: Room,
                                val name: RoomName,
                                val members: List<User>)

    companion object {
        const val EXTRA_ROOM_ID = "room_id"

        private const val REQUEST_SELECT_USER = 1

        private const val MAX_MEMBER_DISPLAY_COUNT = 20

        fun build(context: Context, roomId : String) : Intent {
            return Intent(context, RoomDetailsActivity::class.java).putExtra(EXTRA_ROOM_ID, roomId)
        }
    }
}