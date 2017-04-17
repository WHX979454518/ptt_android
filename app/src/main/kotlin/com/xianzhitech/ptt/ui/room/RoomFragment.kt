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
import android.widget.Toast
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.Permission
import com.xianzhitech.ptt.data.RoomDetails
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.combineLatest
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.ext.isAbsent
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.ext.toLevelString
import com.xianzhitech.ptt.ext.toOptional
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.user.UserDetailsActivity
import com.xianzhitech.ptt.ui.user.UserItemHolder
import com.xianzhitech.ptt.ui.user.UserListAdapter
import com.xianzhitech.ptt.ui.widget.drawable.createAvatarDrawable
import com.xianzhitech.ptt.util.SimpleAnimatorListener
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit

class RoomFragment : BaseFragment()
        , BackPressable {

    private class Views(rootView: View,
                        val titleView: TextView = rootView.findView(R.id.room_title),
                        val notificationView: ImageView = rootView.findView(R.id.room_notification),
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
    private val onlineUserAdapter = object : UserListAdapter(R.layout.view_room_online_member_list_item) {
        override fun onBindViewHolder(holder: UserItemHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            holder.itemView.findViewById(R.id.userItem_initiatorLabel)?.setVisible(holder.userId == appComponent.signalBroker.currentWalkieRoomState.value.currentRoomInitiatorUserId)
        }
    }
    private var popupWindow: PopupWindow? = null
    private var invitationSubscription: Disposable? = null
    private var updateDurationSubscription: Disposable? = null
    private val onlineUserColumnSpan: Int by lazy {
        resources.getInteger(R.integer.horizontal_member_item_count)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_room, container, false)
        val views = Views(rootView)

        rootView.findView<ImageView>(R.id.room_leave).let {
            it.setImageDrawable(context.getTintedDrawable(R.drawable.ic_power, ContextCompat.getColor(context, R.color.red)))

            it.setOnClickListener {
                appComponent.signalBroker.quitWalkieRoom()
                activity.finish()
            }
        }


        rootView.findViewById(R.id.room_info)?.setOnClickListener {
            val roomId = appComponent.signalBroker.peekWalkieRoomId()
            if (roomId != null) {
                activity.startActivityWithAnimation(RoomDetailsActivity.build(context, roomId))
            }
        }

        views.notificationView.setOnClickListener {
            views.notificationView.isEnabled = false
            val roomId = appComponent.signalBroker.peekWalkieRoomId()
            if (roomId != null) {
                appComponent.signalBroker.inviteRoomMembers(roomId)
                        .timeout(Constants.INVITE_MEMBER_TIME_OUT_MILLS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                        .toMaybe()
                        .observeOn(AndroidSchedulers.mainThread())
                        .logErrorAndForget {
                            Toast.makeText(context, getString(R.string.member_invitation_sent_failed, it.describeInHumanMessage(context)),
                                    Toast.LENGTH_LONG).show()
                            views.notificationView.isEnabled = true
                        }
                        .subscribe { value ->
                            val msg: String
                            if (value > 0) {
                                msg = getString(R.string.member_invitation_sent, value)
                            } else {
                                msg = getString(R.string.member_invitation_sent_none)
                            }

                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            views.notificationView.isEnabled = true
                        }
                        .bindToLifecycle()

            }
        }


        appComponent.signalBroker.currentUser
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { user ->
                    val color = if (user.isPresent && user.get().hasPermission(Permission.FORCE_INVITE)) {
                        ContextCompat.getColor(context, R.color.red)
                    } else {
                        0
                    }

                    views.notificationView.setColorFilter(color)
                }
                .bindToLifecycle()

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

        this.views = views
        return rootView
    }

    override fun onDestroyView() {
        invitationSubscription?.dispose()
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
            val currentRoomId = appComponent.signalBroker.peekWalkieRoomId()
            if (currentRoomId != null) {
                val args = Bundle(1).apply { putString(RoomMemberListFragment.ARG_ROOM_ID, currentRoomId) }
                activity.startActivityWithAnimation(
                        FragmentDisplayActivity.createIntent(RoomMemberListFragment::class.java, args)
                )
            }
        }
        return view
    }

    override fun onStart() {
        super.onStart()

        val signalService = appComponent.signalBroker
        val stateByRoomId = signalService.currentWalkieRoomId

        combineLatest(
                signalService.currentWalkieRoomState.map(RoomState::onlineMemberIds).distinctUntilChanged(),
                stateByRoomId.switchMap {
                    if (it.isPresent) {
                        appComponent.storage.getRoomDetails(it.get())
                    } else {
                        Observable.empty()
                    }
                },
                { onlineMemberIds, roomDetails -> roomDetails.transform { RoomInfo(it!!, onlineMemberIds) } })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { room ->
                    if (room.isPresent) {
                        callbacks<Callbacks>()?.setTitle(room.get().details.name)
                        views?.titleView?.apply {
                            text = R.string.room_name_with_numbers.toFormattedString(context,
                                    room.get().details.name, room.get().onlineMemberIds.size, room.get().onlineMemberIds.size)
                            isEnabled = true
                        }
                    }
                }
                .bindToLifecycle()

        combineLatest(
                signalService.currentWalkieRoomState.map(RoomState::onlineMemberIds)
                        .distinctUntilChanged()
                        .switchMap(appComponent.storage::getUsers),

                signalService.currentWalkieRoomState
                        .map { it.currentRoomInitiatorUserId.toOptional() }
                        .distinctUntilChanged(),

                ::Pair)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (onlineMembers, initiatorUserId) ->
                    onlineUserAdapter.setUsers(onlineMembers)
                    if (initiatorUserId.isPresent) {
                        onlineUserAdapter.setUserToPosition(initiatorUserId.get(), 0)
                    }
                }
                .bindToLifecycle()

        signalService.currentWalkieRoomState.distinctUntilChanged(RoomState::speakerId)
                .switchMap {
                    if (it.speakerId != null) {
                        appComponent.storage.getUser(it.speakerId)
                    } else {
                        Observable.empty()
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget()
                .subscribe { speaker ->
                    views?.apply {
                        if (speaker.isAbsent) {
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
                        } else if (speakerView.tag != speaker.get()) {
                            speakerNameView.text = R.string.name_with_level.toFormattedString(context, speaker.get().name, speaker.get().priority.toLevelString())
                            speakerAvatarView.setImageDrawable(speaker.get().createAvatarDrawable())
                            speakerAnimationView.setImageDrawable(ContextCompat.getDrawable(context, if (speaker.get().id == appComponent.signalBroker.peekUserId()) R.drawable.sending else R.drawable.receiving))
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
                .bindToLifecycle()
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
        updateDurationSubscription?.dispose()
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

    private data class RoomInfo(val details: RoomDetails,
                                val onlineMemberIds: Collection<String>)

    interface Callbacks {
        fun setTitle(title: CharSequence)
        fun onRoomQuited()
    }
}