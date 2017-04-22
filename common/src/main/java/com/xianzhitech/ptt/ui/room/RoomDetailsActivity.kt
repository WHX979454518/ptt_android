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
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.app.TextInputActivity
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.contact.ContactSelectionFragment
import com.xianzhitech.ptt.ui.modellist.ModelListFragment
import com.xianzhitech.ptt.ui.user.UserDetailsActivity
import com.xianzhitech.ptt.ui.user.UserItemHolder
import com.xianzhitech.ptt.ui.user.UserListAdapter
import com.xianzhitech.ptt.viewmodel.ContactSelectionListViewModel
import io.reactivex.CompletableObserver
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function3
import java.util.concurrent.TimeUnit

class RoomDetailsActivity : BaseActivity(), View.OnClickListener {
    private lateinit var memberView: RecyclerView
    private lateinit var allMemberLabelView: TextView
    private lateinit var roomNameView: TextView
    private lateinit var joinRoomButton: TextView

    private lateinit var appComponent: AppComponent

    private val roomId: String
        get() = intent.getStringExtra(EXTRA_ROOM_ID)

    private val memberAdapter = Adapter()
    private var roomMembers = listOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_room_details)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
            joinRoom(roomId, false)
        }
    }

    override fun onStart() {
        super.onStart()

        appComponent.signalBroker.updateRoom(roomId)

        Observable.combineLatest(
                appComponent.storage.getRoom(roomId).map { it.orNull() ?: throw StaticUserException(R.string.error_room_not_exists) },
                appComponent.storage.getRoomName(roomId),
                appComponent.storage.getRoomMembers(roomId),
                Function3(::RoomData))
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget {
                    it.toast()
                    finish()
                }
                .subscribe { (room, name, members) -> onRoomLoaded(room, name, members) }
                .bindToLifecycle()
    }

    private fun onRoomLoaded(room: Room, roomName: String, roomMembers: List<User>) {
        this.roomMembers = roomMembers
        roomNameView.text = roomName

        if (room.extraMemberIds.isNotEmpty()) {
            // 如果有额外的用户, 意味着这个组是个临时组, 可以改名
            (roomNameView.parent as View).setOnClickListener {
                startActivityForResultWithAnimation(TextInputActivity.build(
                        this, R.string.room_name.toFormattedString(this), R.string.type_room_name.toFormattedString(this), null, false), REQUEST_UPDATE_ROOM_NAME)
            }
            roomNameView.setCompoundDrawablesWithIntrinsicBounds(null, null, getTintedDrawable(R.drawable.ic_arrow_right, roomNameView.currentTextColor), null)
        } else {
            (roomNameView.parent as View).setOnClickListener(null)
            roomNameView.setCompoundDrawables(null, null, null, null)
        }

        // Setup label
        allMemberLabelView.text = R.string.all_member_with_number.toFormattedString(this, roomMembers.size)
        // Set up all member views
        allMemberLabelView.setOnClickListener {
            val args = Bundle(1).apply { putString(RoomMemberListFragment.ARG_ROOM_ID, room.id) }

            startActivityWithAnimation(
                    FragmentDisplayActivity.createIntent(RoomMemberListFragment::class.java, args)
            )
        }

        // Display members
        memberAdapter.setUsers(roomMembers.subList(0, Math.min(roomMembers.size, MAX_MEMBER_DISPLAY_COUNT)))

        if (room.id == appComponent.signalBroker.peekWalkieRoomId()) {
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
            val selectedUserIds = data.getStringArrayListExtra(ModelListFragment.RESULT_EXTRA_SELECTED_IDS)
            appComponent.signalBroker.addRoomMembers(roomId, selectedUserIds.toList())
                    .timeout(Constants.UPDATE_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .toCompletable()
                    .subscribe(RoomUpdateSubscriber(applicationContext, roomId))
        } else if (requestCode == REQUEST_UPDATE_ROOM_NAME && resultCode == RESULT_OK && data != null) {
            val roomName = data.getStringExtra(TextInputActivity.RESULT_EXTRA_TEXT)
            if (roomName.isNullOrBlank()) {
                Toast.makeText(this, R.string.room_not_updated, Toast.LENGTH_LONG).show()
            } else {
                appComponent.storage.updateRoomName(roomId, roomName).logErrorAndForget().subscribe()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private class RoomUpdateSubscriber(private val context: Context,
                                       private val roomId: String) : CompletableObserver {

        override fun onSubscribe(d: Disposable?) {
        }

        override fun onError(e: Throwable) {
            e.toast()
        }

        override fun onComplete() {
            Toast.makeText(context, R.string.room_updated.toFormattedString(context), Toast.LENGTH_LONG).show()
            (context.applicationContext as AppComponent).storage.updateRoomLastWalkieActiveTime(roomId).logErrorAndForget().subscribe()
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
                    val args = Bundle(1).apply {
                        putStringArrayList(ContactSelectionFragment.ARG_PRESELECTED_MODEL_IDS, ArrayList(roomMembers.map(User::id)))
                    }

                    startActivityForResultWithAnimation(
                            FragmentDisplayActivity.createIntent(ContactSelectionFragment::class.java, args),
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
                                val name: String,
                                val members: List<User>)

    companion object {
        const val EXTRA_ROOM_ID = "room_id"

        private const val REQUEST_SELECT_USER = 1
        private const val REQUEST_UPDATE_ROOM_NAME = 2

        private const val MAX_MEMBER_DISPLAY_COUNT = 14

        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_ADD = 1

        fun build(context: Context, roomId: String): Intent {
            return Intent(context, RoomDetailsActivity::class.java).putExtra(EXTRA_ROOM_ID, roomId)
        }
    }
}