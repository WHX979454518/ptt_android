package com.xianzhitech.ptt.ui.room

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AppCompatDialogFragment
import android.support.v7.app.NotificationCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.repo.optUser
import com.xianzhitech.ptt.service.InviteToJoin
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.ui.base.BaseActivity
import rx.Subscription
import java.io.Serializable

class InviteToJoinDialogFragment : AppCompatDialogFragment() {

    private var multiMode = false
    private val adapter = Adapter()
    private var bottomBar : View? = null
    private var title : View? = null

    private val invites = arrayListOf<InviteToJoin>()
    private lateinit var notificationManager : NotificationManager
    private var notificationSubscription: Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NO_TITLE, theme)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun addInvite(invite: InviteToJoin) {
        val existInviteIndex = invites.indexOfFirst { it.roomId == invite.roomId }
        val updateNotification : Boolean
        if (existInviteIndex < 0) {
            invites.add(invite)
            invites.sortBy { it.inviteTime }
            if (invites.size > 1) {
                multiMode = true
            }
            applyMultiMode()
            adapter.notifyDataSetChanged()
            updateNotification = true
        }
        else if (invite.inviteTime.compareTo(invites[existInviteIndex].inviteTime) > 0 && multiMode) {
            invites[existInviteIndex] = invite
            adapter.notifyItemChanged(existInviteIndex)
            updateNotification = true
        }
        else {
            updateNotification = false
        }

        if (updateNotification) {
            updateNotification(invite)
        }
    }

    private fun updateNotification(invite: InviteToJoin) {
        notificationSubscription?.unsubscribe()
        notificationSubscription = (context.applicationContext as AppComponent).userRepository
                .getUser(invite.inviterId)
                .map { it ?: throw StaticUserException(R.string.error_no_such_user) }
                .first()
                .observeOnMainThread()
                .subscribeSimple { user ->
                    NotificationCompat.Builder(context).apply {
                        mContentTitle = R.string.app_name.toFormattedString(context)

                        mContentText = if (multiMode) {
                            R.string.multiple_invite_to_join.toFormattedString(context)
                        } else {
                            R.string.invite_you_to_join_by_whom.toFormattedString(context, user.name)
                        }

                        setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        setSmallIcon(R.drawable.ic_notification_logged_on)
                        setVibrate(longArrayOf(0, 200L, 700L, 200L))
                        setAutoCancel(true)

                        setContentIntent(PendingIntent.getActivities(context, 0,
                                arrayOf(Intent(context, RoomActivity::class.java)
                                        .putExtra(BaseActivity.EXTRA_INVITES_TO_JOIN, invites)
                                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)), 0))
                    }.let {
                        notificationManager.notify(R.id.notification_invite, it.build())
                    }
                }
    }

    override fun onCancel(dialog: DialogInterface?) {
        super.onCancel(dialog)

        notificationSubscription?.unsubscribe()
        notificationManager.cancel(R.id.notification_invite)
    }

    private fun applyMultiMode() {
        bottomBar?.setVisible(multiMode)
        title?.setVisible(multiMode)
    }

    private fun addInvites(newInvites: Collection<InviteToJoin>) {
        invites.addAll(newInvites)
        invites.sortBy { it.inviteTime }
        if (invites.size > 1) {
            multiMode = true
        }
        applyMultiMode()
        adapter.notifyDataSetChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_fragment_invite, container).apply {
            val recyclerView = findView<RecyclerView>(R.id.inviteDialog_recyclerView)
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter

            bottomBar = findViewById(R.id.inviteDialog_bottomBar).apply {
                findViewById(R.id.inviteDialog_ignoreAll).setOnClickListener {
                    cancelJoinRoom()
                }
            }

            title = findViewById(R.id.inviteDialog_title)
            applyMultiMode()
        }
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addInvites((savedInstanceState?.getSerializable(STATE_INVITES) ?: arguments.getSerializable(ARG_INITIAL_INVITES)) as List<InviteToJoin>)
        updateNotification(invites[0])
    }

    internal fun joinRoom(invite: InviteToJoin) {
        callbacks<Callbacks>()?.joinRoomFromInvite(invite.roomId)
        dismissImmediately()
    }

    internal fun cancelJoinRoom() {
        dismissImmediately()
    }

    override fun onDestroyView() {
        bottomBar = null
        super.onDestroyView()
    }

    interface Callbacks {
        fun joinRoomFromInvite(roomId : String)
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.invite = invites[position]
        }

        override fun getItemViewType(position: Int): Int {
            return if (multiMode) VIEW_TYPE_MULTIPLE else VIEW_TYPE_SINGLE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder? {
            val layout = when (viewType) {
                VIEW_TYPE_SINGLE -> R.layout.view_invite_single_item
                else -> R.layout.view_invite_multi_item
            }

            return ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        }

        override fun getItemCount() = invites.size
    }

    private inner class ViewHolder(rootView : View,
                                   val inviterIcon : ImageView = rootView.findView(R.id.inviteItem_inviterIcon),
                                   val inviterName : TextView = rootView.findView(R.id.inviteItem_inviterName),
                                   val joinButton : View = rootView.findViewById(R.id.inviteItem_join),
                                   val inviteTime : TextView? = rootView.findViewById(R.id.inviteItem_time) as? TextView,
                                   val cancelButton : View? = rootView.findViewById(R.id.inviteItem_cancel)) : RecyclerView.ViewHolder(rootView) {

        private var subscription : Subscription? = null

        var invite : InviteToJoin? = null
            set(value) {
                if (field != value) {
                    field = value

                    subscription?.unsubscribe()
                    subscription = (itemView.context.applicationContext as AppComponent).userRepository
                            .optUser(value?.inviterId)
                            .first()
                            .observeOnMainThread()
                            .subscribeSimple { inviter ->
                                inviterIcon.setImageDrawable(inviter?.createAvatarDrawable(this@InviteToJoinDialogFragment))
                                inviterName.text = inviter?.name
                                inviteTime?.text = value?.inviteTime?.formatInvite(itemView.context)
                            }
                }
            }

        init {
            joinButton.setOnClickListener {
                invite?.let { joinRoom(it) }
            }

            cancelButton?.setOnClickListener {
                invite?.let { cancelJoinRoom() }
            }
        }
    }

    class Builder {
        var invites : List<InviteToJoin> = arrayListOf()

        fun showImmediately(manager: FragmentManager, tag: String) : InviteToJoinDialogFragment {
            if (invites.isEmpty()) {
                throw IllegalStateException("Has to have more than one invite")
            }

            val fragment = InviteToJoinDialogFragment().apply {
                arguments = Bundle(1).apply {
                    putSerializable(ARG_INITIAL_INVITES, invites as Serializable)
                }
            }

            fragment.show(manager, tag)
            manager.executePendingTransactions()
            return fragment
        }
    }

    companion object {
        private const val VIEW_TYPE_SINGLE = 0
        private const val VIEW_TYPE_MULTIPLE = 1

        const val ARG_INITIAL_INVITES = "arg_ii"
        private const val STATE_INVITES = "state_invites"
    }
}