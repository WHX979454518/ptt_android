package com.xianzhitech.ptt.ui.room

import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.model.Privilege
import com.xianzhitech.ptt.service.provider.*
import com.xianzhitech.ptt.service.room.RoomService
import com.xianzhitech.ptt.service.room.RoomStatus
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.widget.PushToTalkButton
import rx.Observable
import rx.observables.ConnectableObservable
import java.util.*
import kotlin.collections.arrayListOf
import kotlin.collections.hashSetOf
import kotlin.collections.sortWith

/**
 * 显示对话界面

 * Created by fanchao on 11/12/15.
 */
class RoomFragment : BaseFragment<RoomFragment.Callbacks>(), PushToTalkButton.Callbacks {

    private lateinit var toolbar: Toolbar
    private lateinit var pttBtn: PushToTalkButton
    private lateinit var appBar: ViewGroup
    private lateinit var progressBar: View
    private lateinit var roomStatusView: TextView
    private lateinit var memberView: RecyclerView
    private lateinit var broker: Broker

    private val adapter = Adapter()

    internal lateinit var signalProvider: SignalProvider
    internal lateinit var authProvider: AuthProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val component = activity.application as AppComponent
        broker = component.broker
        signalProvider = component.signalProvider
        authProvider = component.authProvider
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_room, container, false)?.apply {
            toolbar = findView(R.id.room_toolbar)
            pttBtn = findView(R.id.room_pushToTalkButton)
            appBar = findView(R.id.room_appBar)
            progressBar = findView(R.id.room_progress)
            roomStatusView = findView(R.id.room_status)
            memberView = findView(R.id.room_memberList)

            memberView.layoutManager = GridLayoutManager(context, 7)
            memberView.adapter = adapter

            toolbar.navigationIcon = context.getTintedDrawable(R.drawable.ic_arrow_back, Color.WHITE)
            toolbar.setNavigationOnClickListener { v -> activity.finish() }
            ViewCompat.setElevation(pttBtn, ViewCompat.getElevation(appBar) + resources.getDimension(R.dimen.divider_normal))
            pttBtn.callbacks = this@RoomFragment
        }
    }

    override fun onStart() {
        super.onStart()

        val request = arguments.getSerializable(ARG_ROOM_REQUEST) as ConversationRequest?

        if (request != null) {
            val conversationIdObservable: ConnectableObservable<String> = if (request is CreateConversationRequest) {
                // 没有会话id, 向服务器请求
                signalProvider.createConversation(request)
                        .flatMap { broker.saveConversation(it) }
                        .map { it.id }.publish()
            } else if (request is ConversationFromExisiting) {
                Observable.just(request.conversationId).publish()
            } else {
                throw IllegalArgumentException("Unknown request " + request)
            }

            // 绑定对讲状态
            RoomService.getRoomStatus(context)
                    .observeOnMainThread()
                    .compose(bindToLifecycle())
                    .subscribe(object : GlobalSubscriber<RoomStatus>(context) {
                        override fun onNext(t: RoomStatus) {
                            logd("$this@RoomFragment received status $t")
                            pttBtn.roomStatus = t
                        }
                    })

            // 绑定当前对讲用户状态
            // 这个临时变量只是用来保持下面一个async call chain的整洁
            val logonPersonObj = authProvider.currentLogonUserId?.let { Person(it, "", EnumSet.noneOf(Privilege::class.java)) }

            RoomService.getCurrentSpeakerId(context)
                    .flatMap {
                        when (it) {
                            null -> Observable.just(null)
                            authProvider.currentLogonUserId -> logonPersonObj.toObservable()
                            else -> broker.getPerson(it)
                        }
                    }
                    .observeOnMainThread()
                    .compose(bindToLifecycle())
                    .subscribe {
                        if (it == null) {
                            roomStatusView.animate().alpha(0f).start()
                        } else if (authProvider.currentLogonUserId == it.id) {
                            roomStatusView.animate().alpha(1f).start()
                            roomStatusView.text = R.string.room_talking.toFormattedString(context)
                        } else {
                            roomStatusView.animate().alpha(1f).start()
                            roomStatusView.text = R.string.room_other_is_talking.toFormattedString(context, it.name)
                        }

                        adapter.currentSpeakerId = it?.id
                    }

            // 绑定房间的成员
            Observable.combineLatest(conversationIdObservable.flatMap { broker.getConversationMembers(it) },
                    conversationIdObservable.flatMap { signalProvider.getConversationActiveMemberIds(it) },
                    { first, second -> Pair(first, second) })
                    .observeOnMainThread()
                    .compose(bindToLifecycle())
                    .subscribe { adapter.setMembers(it.first, it.second) }

            // 请求连接房间
            conversationIdObservable
                    .flatMap { broker.getConversation(it) }
                    .observeOnMainThread()
                    .compose(bindToLifecycle())
                    .subscribe(object : GlobalSubscriber<Conversation>(context) {
                        override fun onError(e: Throwable) {
                            AlertDialog.Builder(context).setMessage("Joined room failed: " + e.message).create().show()
                            progressBar.visibility = View.GONE
                        }

                        override fun onNext(t: Conversation) {
                            progressBar.visibility = View.GONE
                            context.startService(RoomService.buildConnect(context, t.id))
                            toolbar.title = t.name
                        }
                    })

            conversationIdObservable.connect()
        }
    }

    override fun onStop() {
        super.onStop()

        context.startService(RoomService.buildDisconnect(context))
    }

    override fun requestFocus() {
        context.startService(RoomService.buildRequestFocus(context, true))
    }

    override fun releaseFocus() {
        context.startService(RoomService.buildRequestFocus(context, false))
    }

    interface Callbacks

    private class Adapter : RecyclerView.Adapter<ViewHolder>(), Comparator<Person> {
        val activeMembers = hashSetOf<String>()
        val members = arrayListOf<Person>()
        var currentSpeakerId: String? = null
            set(newSpeaker) {
                if (field != newSpeaker) {
                    field = newSpeaker
                    notifyDataSetChanged()
                }
            }

        fun setMembers(newMembers: Collection<Person>?, newActiveMembers: Collection<String>?) {
            members.clear()
            newMembers?.let { members.addAll(it); members.sortWith(this) }
            activeMembers.clear()
            newActiveMembers?.let { activeMembers.addAll(it) }
            notifyDataSetChanged()
        }

        override fun compare(lhs: Person, rhs: Person): Int {
            val lhsActive = activeMembers.contains(lhs.id)
            val rhsActive = activeMembers.contains(rhs.id)
            if (lhsActive && rhsActive || (!lhsActive && !rhsActive)) {
                return lhs.name.compareTo(rhs.name)
            } else if (lhsActive) {
                return 1
            } else {
                return -1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = ViewHolder(parent!!)

        override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
            holder?.imageView?.let {
                val person = members[position]
                it.setImageDrawable(person.getIcon(it.context))
                it.isEnabled = activeMembers.contains(person.id)
                it.isSelected = currentSpeakerId == person.id
            }
        }

        override fun getItemCount() = members.size
    }

    private class ViewHolder(container: ViewGroup,
                             val imageView: ImageView = LayoutInflater.from(container.context).inflate(R.layout.view_room_member_item, container, false) as ImageView)
    : RecyclerView.ViewHolder(imageView)

    companion object {

        val ARG_ROOM_REQUEST = "arg_room_request"

        fun create(request: ConversationRequest): Fragment {
            val fragment = RoomFragment()
            val args = Bundle(1)
            args.putSerializable(ARG_ROOM_REQUEST, request)
            fragment.arguments = args
            return fragment
        }
    }
}
