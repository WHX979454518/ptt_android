package com.xianzhitech.ptt.ui.room

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.globalHandleError
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.service.roomStatus
import com.xianzhitech.ptt.ui.MainActivity
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity
import rx.Completable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

/**
 * 房间对话界面

 * Created by fanchao on 11/12/15.
 */
class RoomActivity : BaseActivity(), RoomFragment.Callbacks {

    private lateinit var signalService: SignalService
    private lateinit var titleView : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_room)
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        supportFragmentManager.apply {
            if (findFragmentById(R.id.room_content) == null) {
                beginTransaction()
                        .replace(R.id.room_content, RoomFragment())
                        .commit()
                executePendingTransactions()
            }
        }
    }

    override fun onBackPressed() {
        supportFragmentManager.findFragmentById(R.id.room_content) ?. let {
            if (it is BackPressable && it.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }

    override fun onRoomQuited() {
        finish()
    }

    override fun onStart() {
        super.onStart()

        (application as AppComponent).signalService
                .roomStatus
                .observeOnMainThread()
                .bindToLifecycle()
                .subscribeSimple {
                    if (it == RoomStatus.JOINING) {
                        showProgressDialog(R.string.joining_room, TAG_JOIN_ROOM_PROGRESS)
                    } else {
                        hideProgressDialog(TAG_JOIN_ROOM_PROGRESS)
                    }
                }
    }

    override fun joinRoomConfirmed(roomId: String) {
        val appComponent = application as AppComponent

        appComponent.signalService.joinRoom(roomId)
                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(JoinRoomSubscriber(applicationContext, roomId))
    }

    private class JoinRoomSubscriber(private val appContext : Context,
                                     private val roomId : String) : Completable.CompletableSubscriber {
        override fun onSubscribe(d: Subscription?) { }
        override fun onError(e: Throwable) {
            globalHandleError(e, appContext)

            val appComponent = appContext as AppComponent
            val activity = appComponent.activityProvider.currentStartedActivity
            if (activity is RoomActivity) {
                activity.finish()
            } else {
                appContext.startActivity(Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }

            appComponent.signalService.leaveRoom().subscribeSimple()
        }

        override fun onCompleted() {
            val appComponent = appContext as AppComponent
            appComponent.roomRepository.updateLastRoomActiveTime(roomId).execAsync().subscribeSimple()
        }
    }

    companion object {
        private const val TAG_JOIN_ROOM_PROGRESS = "tag_join_room_progress"
    }

}
