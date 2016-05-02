package com.xianzhitech.ptt.ui.invite

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Bundle
import android.support.v7.app.NotificationCompat
import android.support.v7.util.SortedList
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.util.SortedListAdapterCallback
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.optUser
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.ui.base.BaseActivity
import rx.Observable
import rx.Subscription

class RoomInvitationActivity : BaseActivity() {
    companion object {
        private const val VIEW_TYPE_MULTIPLE = 1
        private const val VIEW_TYPE_SINGLE = 2

        const val EXTRA_NEW_INVITE = "extra_new_invite"
    }

    private val adapter = Adapter()
    private val invites = SortedList<RoomInvitation>(RoomInvitation::class.java,
            object : SortedListAdapterCallback<RoomInvitation>(adapter) {
                override fun areItemsTheSame(item1: RoomInvitation, item2: RoomInvitation): Boolean {
                    return item1.roomId == item2.roomId
                }

                override fun compare(o1: RoomInvitation, o2: RoomInvitation): Int {
                    if (o1.roomId == o2.roomId) {
                        return 0
                    }

                    var rc = o1.inviteTime.compareTo(o2.inviteTime)
                    if (rc != 0) {
                        return rc
                    }

                    return o1.roomId.compareTo(o2.roomId)
                }

                override fun areContentsTheSame(oldItem: RoomInvitation, newItem: RoomInvitation): Boolean {
                    return areItemsTheSame(oldItem, newItem)
                }
            })
    private var multiMode : Boolean = false
    private lateinit var notificationManager : NotificationManager
    private var notificationSubscription: Subscription? = null

    private lateinit var bottomBar : View
    private lateinit var title : View

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        } catch(e: Exception) {
            //FIXME: 为什么有时会挂?
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_invitation)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val recyclerView = findView<RecyclerView>(R.id.invitation_recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        bottomBar = findView<View>(R.id.invitation_bottomBar).apply {
            findViewById(R.id.invitation_ignoreAll).setOnClickListener {
                cancelJoinRoom()
            }
        }

        title = findView<View>(R.id.invitation_title)
        handleIntent(intent)

        if (invites.size() == 0) {
            throw IllegalArgumentException()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleIntent(intent)
    }

    override fun finish() {
        super.finish()

        notificationManager.cancel(R.id.notification_invite)
    }

    private fun handleIntent(intent : Intent) {
        val newInvite = intent.getSerializableExtra(EXTRA_NEW_INVITE) as? RoomInvitation
        if (newInvite != null) {
            invites.add(newInvite)
            if (!multiMode && invites.size() > 1) {
                multiMode = true
                adapter.notifyDataSetChanged()
            }
            intent.removeExtra(EXTRA_NEW_INVITE)
            updateNotification()
        }
        applyMultiMode()
    }

    private fun applyMultiMode() {
        bottomBar.setVisible(multiMode)
        title.setVisible(multiMode)
    }

    private fun updateNotification() {
        notificationSubscription?.unsubscribe()

        val userObservable : Observable<User?>
        if (multiMode) {
            userObservable = Observable.just(null)
        }
        else {
            userObservable = (application as AppComponent).userRepository
                    .getUser(invites[0].inviterId)
                    .map { it ?: throw StaticUserException(R.string.error_no_such_user) }
                    .first()
                    .observeOnMainThread()
                    .compose(bindToLifecycle())
        }

        notificationSubscription = userObservable.subscribeSimple { user ->
            NotificationCompat.Builder(this).apply {
                mContentTitle = R.string.app_name.toFormattedString(this@RoomInvitationActivity)

                mContentText = if (user == null) {
                    R.string.multiple_invite_to_join.toFormattedString(this@RoomInvitationActivity)
                } else {
                    R.string.invite_you_to_join_by_whom.toFormattedString(this@RoomInvitationActivity, user.name)
                }

                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                setSmallIcon(R.drawable.ic_notification_logged_on)
                setVibrate(longArrayOf(0, 200L, 700L, 200L))
                setAutoCancel(true)
                setContentIntent(PendingIntent.getActivities(this@RoomInvitationActivity, 0, arrayOf(
                        Intent(this@RoomInvitationActivity, RoomInvitationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)), 0))

            }.let {
                notificationManager.notify(R.id.notification_invite, it.build())
            }
        }
    }

    fun dismiss() {
        finish()
    }

    internal fun joinRoom(roomInvitation: RoomInvitation) {
        joinRoom(roomInvitation.roomId)
    }

    override fun onRoomJoined() {
        finish()
        super.onRoomJoined()
    }

    internal fun cancelJoinRoom() {
        finish()
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

        override fun getItemCount() = invites.size()
    }

    private inner class ViewHolder(rootView : View,
                                   val inviterIcon : ImageView = rootView.findView(R.id.inviteItem_inviterIcon),
                                   val inviterName : TextView = rootView.findView(R.id.inviteItem_inviterName),
                                   val joinButton : View = rootView.findViewById(R.id.inviteItem_join),
                                   val inviteTime : TextView? = rootView.findViewById(R.id.inviteItem_time) as? TextView,
                                   val cancelButton : View? = rootView.findViewById(R.id.inviteItem_cancel)) : RecyclerView.ViewHolder(rootView) {

        private var subscription : Subscription? = null

        var invite : RoomInvitation? = null
            set(value) {
                if (field != value) {
                    field = value

                    subscription?.unsubscribe()
                    subscription = (itemView.context.applicationContext as AppComponent).userRepository
                            .optUser(value?.inviterId)
                            .first()
                            .observeOnMainThread()
                            .subscribeSimple { inviter ->
                                inviterIcon.setImageDrawable(inviter?.createAvatarDrawable(itemView.context))
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
}