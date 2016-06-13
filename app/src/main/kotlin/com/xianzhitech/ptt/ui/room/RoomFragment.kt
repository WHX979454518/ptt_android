package com.xianzhitech.ptt.ui.room

import android.animation.Animator
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomName
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.home.ModelListActivity
import com.xianzhitech.ptt.ui.user.UserDetailsActivity
import com.xianzhitech.ptt.ui.user.UserListAdapter
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import com.xianzhitech.ptt.util.SimpleAnimatorListener
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class RoomFragment : BaseFragment()
        , BackPressable {

    private class Views(rootView: View,
                        val titleView: TextView = rootView.findView(R.id.room_title),
                        val speakerView: View = rootView.findView(R.id.room_speakerView),
                        val speakerAvatarView: ImageView = speakerView.findView(R.id.room_speakerAvatar),
                        val speakerAnimationView: ImageView = speakerView.findView(R.id.room_speakerAnimationView),
                        val speakerEllapseTimeView: View = speakerView.findView(R.id.room_speakerEllapseTime),
                        val speakerDurationTimeView: TextView = speakerView.findView(R.id.room_speakerDuration),
                        val speakerNameView: TextView = speakerView.findView(R.id.room_speakerName)) {
        var speakerAnimator: ObjectAnimator? = null

        init {
            titleView.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    titleView.context.getTintedDrawable(R.drawable.ic_expand_more_24dp, Color.WHITE), null)
        }
    }

    private var views: Views? = null
    private val onlineUserAdapter = UserListAdapter(R.layout.view_room_member_list_item)
    private var popupWindow: PopupWindow? = null
    private var invitationSubscription: Subscription? = null
    private var updateDurationSubscription: Subscription? = null
    private val onlineUserColumnSpan: Int by lazy {
        resources.getInteger(R.integer.horizontal_member_item_count)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_room, container, false)
        val views = Views(rootView)

        rootView.findView<ImageView>(R.id.room_leave).let {
            it.setImageDrawable(context.getTintedDrawable(R.drawable.ic_power, ContextCompat.getColor(context, R.color.red)))

            it.setOnClickListener {
                appComponent.signalHandler.quitRoom()
                activity.finish()
            }
        }


        rootView.findViewById(R.id.room_info)?.setOnClickListener {
            val roomId = appComponent.signalHandler.currentRoomId
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

        views.speakerView.setOnClickListener {
            val speaker = it.getTag(R.id.key_last_speaker) as? User
            if (speaker != null) {
                activity.startActivityWithAnimation(UserDetailsActivity.build(activity, speaker.id))
            }
        }

        this.views = views;
        return rootView
    }

    override fun onDestroyView() {
        invitationSubscription?.unsubscribe()
        super.onDestroyView()
    }

    private fun ensurePopupWindow(): PopupWindow {
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
            layoutManager = GridLayoutManager(context, onlineUserColumnSpan)
            adapter = onlineUserAdapter
        }
        view.findViewById(R.id.roomOnlineInfo_all)!!.setOnClickListener {
            val currentRoomId = appComponent.signalHandler.currentRoomId
            if (currentRoomId != null) {
                activity.startActivityWithAnimation(
                        ModelListActivity.build(context, R.string.room_members.toFormattedString(context),
                                RoomMemberProvider(currentRoomId, false))
                )
            }
        }
        return view
    }

    override fun onStart() {
        super.onStart()

        val signalService = appComponent.signalHandler
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
                    onlineUserAdapter.setUsers(it.first)
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
                            speakerNameView.text = R.string.name_with_level.toFormattedString(context, speaker.name, speaker.priority.toLevelString(context))
                            speakerAvatarView.setImageDrawable(speaker.createDrawable(context))
                            speakerAnimationView.setImageDrawable(ContextCompat.getDrawable(context, if (speaker.id == appComponent.signalHandler.currentUserId) R.drawable.sending else R.drawable.receiving))
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

                        val animateDrawable = speakerAnimationView.drawable

                        if (speaker == null) {
                            speakerAnimationView.visibility = View.INVISIBLE
                            speakerEllapseTimeView.visibility = View.VISIBLE
                            if (animateDrawable is AnimationDrawable) {
                                animateDrawable.stop()
                            }
                            stopUpdatingDurationView()
                        } else {
                            if (animateDrawable is AnimationDrawable && !animateDrawable.isRunning) {
                                animateDrawable.start()
                            }
                            speakerAnimationView.visibility = View.VISIBLE
                            speakerEllapseTimeView.visibility = View.INVISIBLE
                            startUpdatingDurationView()
                        }

                        speakerView.tag = speaker
                        if (speaker != null) {
                            speakerView.setTag(R.id.key_last_speaker, speaker)
                        }
                    }
                }
    }

    private fun startUpdatingDurationView() {
        if (updateDurationSubscription == null) {
            updateDurationSubscription = Observable.interval(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .subscribe {
                        updateDurationView()
                    }
        }
    }

    private fun stopUpdatingDurationView() {
        updateDurationSubscription?.unsubscribe()
        updateDurationSubscription = null
    }

    private fun updateDurationView() {
        views?.apply {
            speakerDurationTimeView.text = DateUtils.formatElapsedTime(appComponent.statisticCollector.lastSpeakerDuration / 1000)
        }
    }

    override fun onStop() {
        super.onStop()

        stopUpdatingDurationView()
        popupWindow?.dismiss()
    }

    override fun onBackPressed(): Boolean {
        if (popupWindow != null && popupWindow!!.isShowing) {
            popupWindow!!.dismiss()
            return true
        }

        return false
    }

    private data class RoomInfo(val roomName: RoomName?,
                                val room: Room?,
                                val roomMembers: List<User>,
                                val onlineMemberIds: Collection<String>)

    interface Callbacks {
        fun setTitle(title: CharSequence)
        fun onRoomQuited()
    }
}