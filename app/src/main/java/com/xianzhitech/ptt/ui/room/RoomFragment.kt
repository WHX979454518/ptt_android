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
import butterknife.Bind
import butterknife.ButterKnife
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Broker
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.provider.ConversationRequest
import com.xianzhitech.ptt.service.provider.CreateConversationRequest
import com.xianzhitech.ptt.service.provider.ExistingConversationRequest
import com.xianzhitech.ptt.service.provider.SignalProvider
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.util.ResourceUtil
import com.xianzhitech.ptt.ui.widget.PushToTalkButton
import rx.Observable
import rx.Subscriber
import rx.android.schedulers.AndroidSchedulers

/**
 * 显示对话界面

 * Created by fanchao on 11/12/15.
 */
class RoomFragment : BaseFragment<RoomFragment.Callbacks>() {

    @Bind(R.id.room_toolbar)
    internal lateinit var toolbar: Toolbar

    @Bind(R.id.room_pushToTalkButton)
    internal lateinit var pttBtn: PushToTalkButton

    @Bind(R.id.room_appBar)
    internal lateinit var appBar: ViewGroup

    @Bind(R.id.room_progress)
    internal lateinit var progressBar: View

    internal lateinit var broker: Broker

    internal lateinit var signalProvider: SignalProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val component = activity.application as AppComponent
        broker = component.broker
        signalProvider = component.signalProvider
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_room, container, false)
        ButterKnife.bind(this, view)
        toolbar.navigationIcon = ResourceUtil.getTintedDrawable(context, R.drawable.ic_arrow_back, Color.WHITE)
        toolbar.setNavigationOnClickListener { v -> activity.finish() }
        ViewCompat.setElevation(pttBtn, ViewCompat.getElevation(appBar) + resources.getDimension(R.dimen.divider_normal))
        return view
    }

    override fun onDestroyView() {
        ButterKnife.unbind(this)
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()

        val request = arguments.getSerializable(ARG_ROOM_REQUEST) as ConversationRequest?

        if (request != null) {
            val conversationIdObservable: Observable<String>

            if (request is CreateConversationRequest) {
                // 没有会话id, 向服务器请求
                conversationIdObservable = signalProvider.createConversation(setOf(request))
                        .flatMap({ broker.saveConversation(it) })
                        .map({ it.id })
            } else if (request is ExistingConversationRequest) {
                conversationIdObservable = Observable.just(request.conversationId)
            } else {
                throw IllegalArgumentException("Unknown request " + request)
            }

            conversationIdObservable.flatMap({ signalProvider.joinConversation(it) })
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(this.bindToLifecycle<Room>())
                    .subscribe(object : Subscriber<Room>() {
                        override fun onCompleted() {
                            progressBar.visibility = View.GONE
                        }

                        override fun onError(e: Throwable) {
                            AlertDialog.Builder(context).setMessage("Joined room failed: " + e.message).create().show()
                            progressBar.visibility = View.GONE
                        }

                        override fun onNext(room: Room) {
                            AlertDialog.Builder(context).setMessage("Joined room: " + room).create().show()
                        }
                    })
        }
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
