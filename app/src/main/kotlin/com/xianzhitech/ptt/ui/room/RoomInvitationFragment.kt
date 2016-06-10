package com.xianzhitech.ptt.ui.room

import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.user.UserItemHolder
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.*


class RoomInvitationFragment : BaseFragment() {

    private val pendingInvitations = arrayListOf<RoomInvitation>()
    private var subscription: Subscription? = null

    private lateinit var views: Views

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            pendingInvitations.addAll(savedInstanceState.getSerializable(STATE_PENDING_INVITATIONS) as ArrayList<RoomInvitation>)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable(STATE_PENDING_INVITATIONS, pendingInvitations)
    }

    fun addInvitations(invitations: Collection<RoomInvitation>) {
        pendingInvitations.addAll(invitations)
        if (view != null) {
            applyView()
        }
    }

    fun removeRoomInvitation(roomId: String) {
        if (pendingInvitations.removeAll { it.roomId == roomId } && view != null) {
            applyView()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_room_invitation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        views = Views(view).apply {
            ignoreButton.setOnClickListener { onIgnore(pendingInvitations.first()) }
            acceptButton.setOnClickListener { onAccept(pendingInvitations.first()) }
            viewButton.setOnClickListener { onViewDetails() }
        }

        applyView()
    }

    override fun onDestroyView() {
        subscription?.unsubscribe()
        subscription = null
        super.onDestroyView()
    }

    private fun onViewDetails() {
        callbacks<Callbacks>()?.showInvitationList(pendingInvitations)
    }

    fun ignoreAllInvitations() {
        pendingInvitations.clear()
        applyView()
    }

    private fun onAccept(invitation: RoomInvitation) {
        (activity as? BaseActivity)?.joinRoom(invitation.roomId)
        if (pendingInvitations.remove(invitation)) {
            applyView()
        }
    }

    private fun onIgnore(invitation: RoomInvitation) {
        if (pendingInvitations.remove(invitation)) {
            applyView()
        }
    }

    private fun applyView() {
        subscription?.unsubscribe()
        if (pendingInvitations.isEmpty()) {
            callbacks<Callbacks>()?.dismissInvitations()
        } else {
            subscription = appComponent.userRepository
                    .getUsers(pendingInvitations.map { it.inviterId })
                    .getAsync()
                    .toObservable()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeSimple {
                        val users = it
                        if (users.isEmpty()) {
                            callbacks<Callbacks>()?.dismissInvitations()
                            return@subscribeSimple
                        }

                        views.inviteeIconContainer.removeAllViews()
                        val size = Math.min(3, users.size)
                        for (i in 0..size - 1) {
                            val holder = UserItemHolder(views.inviteeIconContainer, R.layout.view_invitee_icon)
                            holder.setUser(users[i])
                            views.inviteeIconContainer.addView(holder.itemView)
                        }

                        val msg: CharSequence = if (size > 1) {
                            R.string.multiple_invite_to_join.toFormattedString(context)
                        } else {
                            val name = users.first().name
                            SpannableStringBuilder(R.string.invite_you_to_join_by_whom.toFormattedString(context, name)).apply {
                                setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.material_deep_teal_500)), 0, name.length, SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE)
                            }
                        }

                        views.titleView.text = msg
                        views.acceptButton.setVisible(size == 1)
                        views.ignoreButton.setVisible(size == 1)
                        views.viewButton.setVisible(size > 1)
                    }
        }
    }

    private class Views(rootView: View,
                        val inviteeIconContainer: ViewGroup = rootView.findView(R.id.roomInvite_iconContainer),
                        val titleView: TextView = rootView.findView(R.id.roomInvite_title),
                        val ignoreButton: View = rootView.findView(R.id.roomInvite_ignore),
                        val acceptButton: View = rootView.findView(R.id.roomInvite_join),
                        val viewButton: View = rootView.findView(R.id.roomInvite_view))

    interface Callbacks {
        fun dismissInvitations()
        fun showInvitationList(invitations: List<RoomInvitation>)
    }

    companion object {
        private const val STATE_PENDING_INVITATIONS = "state_pending_invites"
    }
}