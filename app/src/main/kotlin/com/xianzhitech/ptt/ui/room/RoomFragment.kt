package com.xianzhitech.ptt.ui.room

import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.provider.ConversationFromExisiting
import com.xianzhitech.ptt.service.provider.ConversationRequest
import com.xianzhitech.ptt.service.provider.CreateConversationRequest
import com.xianzhitech.ptt.service.provider.SignalProvider
import com.xianzhitech.ptt.service.room.RoomService
import com.xianzhitech.ptt.service.room.RoomStatus
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.widget.PushToTalkButton
import rx.Observable

/**
 * 显示对话界面

 * Created by fanchao on 11/12/15.
 */
class RoomFragment : BaseFragment<RoomFragment.Callbacks>(), PushToTalkButton.Callbacks {

    private lateinit var toolbar: Toolbar
    private lateinit var pttBtn: PushToTalkButton
    private lateinit var appBar: ViewGroup
    private lateinit var progressBar: View
    private lateinit var broker: Broker

    internal lateinit var signalProvider: SignalProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val component = activity.application as AppComponent
        broker = component.broker
        signalProvider = component.signalProvider
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_room, container, false)?.apply {
            toolbar = findView(R.id.room_toolbar)
            pttBtn = findView(R.id.room_pushToTalkButton)
            appBar = findView(R.id.room_appBar)
            progressBar = findView(R.id.room_progress)

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
            val conversationIdObservable: Observable<String>

            if (request is CreateConversationRequest) {
                // 没有会话id, 向服务器请求
                conversationIdObservable = signalProvider.createConversation(request)
                        .flatMap({ broker.saveConversation(it) })
                        .map({ it.id })
            } else if (request is ConversationFromExisiting) {
                conversationIdObservable = Observable.just(request.conversationId)
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

            // 请求连接房间
            conversationIdObservable
                    .observeOnMainThread()
                    .compose(bindToLifecycle())
                    .subscribe(object : GlobalSubscriber<String>(context) {
                        override fun onError(e: Throwable) {
                            AlertDialog.Builder(context).setMessage("Joined room failed: " + e.message).create().show()
                            progressBar.visibility = View.GONE
                        }

                        override fun onNext(t: String) {
                            progressBar.visibility = View.GONE
                            context.startService(RoomService.buildConnect(context, t))
                        }
                    })


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
