package com.xianzhitech.ptt.ui.room

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.service.currentRoomId
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import com.xianzhitech.ptt.ui.user.*
import rx.Completable
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class RoomDetailsActivity : BaseToolbarActivity(), View.OnClickListener {
    private lateinit var memberView : RecyclerView
    private lateinit var allMemberLabelView : TextView
    private lateinit var roomNameView : TextView
    private lateinit var joinRoomButton : TextView

    private lateinit var appComponent : AppComponent

    private val roomId : String
        get() = intent.getStringExtra(EXTRA_ROOM_ID)

    private val memberAdapter = Adapter()
    private var roomMembers = listOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_room_details)

        memberView = findView(R.id.roomDetails_members)
        allMemberLabelView = findView(R.id.roomDetails_allMemberLabel)
        roomNameView = findView(R.id.roomDetails_name)
        joinRoomButton = findView(R.id.roomDetails_join)
        appComponent = application as AppComponent

        memberView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.horizontal_member_item_count))
        memberView.adapter = memberAdapter

        title = R.string.room_info.toFormattedString(this)

        joinRoomButton.isEnabled = false
        joinRoomButton.setOnClickListener {
            joinRoom(roomId)
        }
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
                    RoomData(room, name!!, members)
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
        this.roomMembers = roomMembers
        roomNameView.text = roomName.name

        // Setup label
        allMemberLabelView.text = R.string.all_member_with_number.toFormattedString(this, roomMembers.size)
        // Set up all member views
        allMemberLabelView.setOnClickListener {
            startActivityWithAnimation(
                    UserListActivity.build(this, R.string.room_members.toFormattedString(this),
                            RoomMemberProvider(room.id), false, emptyList(), false)
            )
        }

        // Display members
        memberAdapter.setUsers(roomMembers.subList(0, Math.min(roomMembers.size, MAX_MEMBER_DISPLAY_COUNT)))

        if (room.id == appComponent.signalService.currentRoomId) {
            joinRoomButton.setText(R.string.in_room)
            joinRoomButton.isEnabled = false
        } else {
            joinRoomButton.setText(R.string.join_room)
            joinRoomButton.isEnabled = true
        }
    }

    override fun onClick(v: View) {
        val memberItemHolder = v.tag as? UserItemHolder
        if (memberItemHolder != null) {
            startActivityWithAnimation(UserDetailsActivity.build(this, memberItemHolder.userId!!))
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_SELECT_USER && resultCode == RESULT_OK && data != null) {
            val selectedUserIds = data.getStringArrayExtra(UserListActivity.RESULT_EXTRA_SELECTED_USER_IDS)
            (application as AppComponent).signalService.updateRoomMembers(roomId, selectedUserIds.toList())
                    .timeout(Constants.UPDATE_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(RoomUpdateSubscriber(applicationContext, roomId))
        }
        else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private class RoomUpdateSubscriber(private val context: Context,
                                       private val roomId : String) : Completable.CompletableSubscriber {

        override fun onSubscribe(d: Subscription?) { }

        override fun onError(e: Throwable) {
            globalHandleError(e, context)
        }

        override fun onCompleted() {
            Toast.makeText(context, R.string.room_updated.toFormattedString(context), Toast.LENGTH_LONG).show()
            (context.applicationContext as AppComponent).roomRepository.updateLastRoomActiveTime(roomId).execAsync().subscribeSimple()
        }
    }

    private inner class Adapter : UserListAdapter(R.layout.view_room_member_list_item) {
        override fun getItemCount(): Int {
            val realCount = super.getItemCount()
            return if (realCount == 0) {
                0
            } else {
                realCount + 1
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == itemCount - 1) {
                VIEW_TYPE_ADD
            } else {
                VIEW_TYPE_USER
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserItemHolder? {
            return if (viewType == VIEW_TYPE_ADD) {
                val holder = UserItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_room_member_list_item, parent, false))
                holder.nameView?.text = ""
                holder.avatarView!!.setImageDrawable(holder.itemView.context.getTintedDrawable(R.drawable.ic_person_add_24dp, holder.nameView!!.currentTextColor))
                holder.itemView.setOnClickListener {
                    startActivityForResultWithAnimation(
                            UserListActivity.build(this@RoomDetailsActivity, R.string.add_members.toFormattedString(parent.context),
                                    ContactUserProvider(), true, roomMembers.map { it.id }, false),
                            REQUEST_SELECT_USER
                    )
                }
                holder
            } else {
                super.onCreateViewHolder(parent, viewType)
            }
        }

        override fun onBindViewHolder(holder: UserItemHolder, position: Int) {
            if (holder.itemViewType == VIEW_TYPE_USER) {
                super.onBindViewHolder(holder, position)
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

        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_ADD = 1

        fun build(context: Context, roomId : String) : Intent {
            return Intent(context, RoomDetailsActivity::class.java).putExtra(EXTRA_ROOM_ID, roomId)
        }
    }
}