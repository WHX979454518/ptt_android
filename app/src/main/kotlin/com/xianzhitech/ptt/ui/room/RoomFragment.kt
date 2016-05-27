package com.xianzhitech.ptt.ui.room

import android.animation.Animator
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.combineWith
import com.xianzhitech.ptt.ext.createAvatarDrawable
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.setVisible
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
import com.xianzhitech.ptt.ui.user.UserItemHolder
import com.xianzhitech.ptt.ui.user.UserListActivity
import com.xianzhitech.ptt.util.SimpleAnimatorListener
import rx.Observable

class RoomFragment : BaseFragment()
        , BackPressable {

    private class Views(rootView: View,
                        val titleView : TextView = rootView.findView(R.id.room_title),
                        val speakerView : View = rootView.findView(R.id.room_speakerView),
                        val speakerAvatarView : ImageView = speakerView.findView(R.id.room_speakerAvatar),
                        val speakerNameView : TextView = speakerView.findView(R.id.room_speakerName)) {
        var speakerAnimator : ObjectAnimator? = null

        init {
            titleView.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    titleView.context.getTintedDrawable(R.drawable.ic_expand_more_24dp, Color.WHITE), null)
        }
    }

    private var views: Views? = null
    private val onlineUserAdapter = OnlineUserAdapter()
    private lateinit var appComponent : AppComponent
    private var popupWindow : PopupWindow? = null

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

        views.titleView.isEnabled = false // Click not allowed when data is loading
        views.titleView.setOnClickListener {
            if (popupWindow != null && popupWindow!!.isShowing) {
                popupWindow?.dismiss()
            } else {
                ensurePopupWindow().showAsDropDown(views.titleView)
            }
        }

        this.views = views;
        return rootView
    }

    private fun ensurePopupWindow() : PopupWindow {
        if (popupWindow == null) {
            popupWindow = PopupWindow(onCreatePopupWindowView(), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            popupWindow!!.isOutsideTouchable = true
            popupWindow!!.isFocusable = true
            popupWindow!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        return popupWindow!!
    }

    private fun onCreatePopupWindowView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.view_room_online_info, null)
        view.findView<RecyclerView>(R.id.roomOnlineInfo_list).apply {
            layoutManager = GridLayoutManager(context, 5)
            adapter = onlineUserAdapter
        }
        view.findViewById(R.id.roomOnlineInfo_all)!!.setOnClickListener {
            val currentRoomId = appComponent.signalService.currentRoomId
            if (currentRoomId != null) {
                activity.startActivityWithAnimation(
                        UserListActivity.build(context, R.string.room_members.toFormattedString(context),
                                RoomMemberProvider(currentRoomId), false, null, emptyList(), false)
                )
            }
        }
        return view
    }

    override fun onStart() {
        super.onStart()

        val signalService = appComponent.signalService
        val stateByRoomId = signalService.roomState.distinctUntilChanged { it.currentRoomId }

        Observable.combineLatest(
                stateByRoomId.switchMap { appComponent.roomRepository.getRoomName(it.currentRoomId, excludeUserIds = arrayOf(signalService.currentUserId)).observe() },
                signalService.roomState.distinctUntilChanged { it.onlineMemberIds }.map { it.onlineMemberIds },
                stateByRoomId.switchMap { appComponent.roomRepository.getRoomMembers(it.currentRoomId, maxMemberCount = Int.MAX_VALUE).observe() },
                stateByRoomId.switchMap { appComponent.roomRepository.getRoom(it.currentRoomId).observe() },
                { roomName, onlineMemberIds, roomMembers, room -> RoomInfo(roomName, room, roomMembers, onlineMemberIds) }
        )
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    if (it.roomName != null && it.room != null) {
                        callbacks<Callbacks>()?.setTitle(it.roomName.name)
                        views?.titleView?.apply {
                            text = R.string.room_name_with_numbers.toFormattedString(context, it.roomName.name, it.onlineMemberIds.size, it.roomMembers.size)
                            isEnabled = true
                        }
                    }
                }

        signalService.roomState.distinctUntilChanged { it.onlineMemberIds }
                .switchMap { appComponent.userRepository.getUsers(it.onlineMemberIds).observe() }
                .combineWith(stateByRoomId.switchMap { appComponent.roomRepository.getRoom(it.currentRoomId).observe() })
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    onlineUserAdapter.setUsers(it.first, it.second)
                }

        signalService.roomState.distinctUntilChanged { it.speakerId }
                .switchMap { appComponent.userRepository.getUser(it.speakerId).observe() }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple { speaker ->
                    views?.apply {
                        if (speaker == null) {
                            speakerAnimator?.cancel()
                            speakerAnimator = ObjectAnimator.ofFloat(speakerView, View.ALPHA, 0f).apply {
                                startDelay = 5000
                                addListener(object : SimpleAnimatorListener() {
                                    override fun onAnimationEnd(animation: Animator?) {
                                        speakerView.setVisible(false)
                                    }
                                })
                                start()
                            }
                        } else if (speakerView.tag != speaker) {
                            speakerNameView.text = speaker.name
                            speakerAvatarView.setImageDrawable(speaker.createAvatarDrawable(this@RoomFragment))

                            speakerAnimator?.cancel()
                            speakerAnimator = ObjectAnimator.ofFloat(speakerView, View.ALPHA, 1f).apply {
                                addListener(object : SimpleAnimatorListener() {
                                    override fun onAnimationStart(animation: Animator?) {
                                        speakerView.setVisible(true)
                                    }
                                })
                                start()
                            }
                        }

                        speakerView.tag = speaker
                    }
                }
    }

    override fun onStop() {
        super.onStop()

        popupWindow?.dismiss()
    }

    override fun onBackPressed(): Boolean {
        if (popupWindow != null && popupWindow!!.isShowing) {
            popupWindow!!.dismiss()
            return true
        }

        return false
    }

    private class OnlineUserAdapter : RecyclerView.Adapter<UserItemHolder>() {
        private var userList = emptyList<User>()

        fun setUsers(users : Collection<User>, room: Room?) {
            this.userList = users.distinctBy { it.id }
            this.userList.sortedWith(RoomMemberComparator(room))
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserItemHolder {
            return UserItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.view_room_member_list_item, parent, false))
        }

        override fun onBindViewHolder(holder: UserItemHolder, position: Int) {
            holder.setUser(userList[position])
        }

        override fun getItemCount(): Int {
            return userList.size
        }
    }

    private data class RoomInfo(val roomName: RoomName?,
                                val room: Room?,
                                val roomMembers: List<User>,
                                val onlineMemberIds: Collection<String>)

    interface Callbacks {
        fun setTitle(title : CharSequence)
        fun onRoomQuited()
    }
}