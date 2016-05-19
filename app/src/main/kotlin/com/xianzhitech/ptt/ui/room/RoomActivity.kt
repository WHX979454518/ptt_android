package com.xianzhitech.ptt.ui.room

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.trello.rxlifecycle.ActivityEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.ensureConnectivity
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.startActivity
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.MainActivity
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

/**
 * 房间对话界面

 * Created by fanchao on 11/12/15.
 */
class RoomActivity : BaseToolbarActivity(), RoomFragment.Callbacks {

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

    override fun onRoomLoaded(name: CharSequence) {
        this.title = name
    }

    override fun onRoomQuited() {
        finish()
    }

    override fun joinRoomConfirmed(roomId: String) {
        val appComponent = application as AppComponent

        showProgressDialog(R.string.please_wait, R.string.joining_room, TAG_JOIN_ROOM_PROGRESS)
        ensureConnectivity()
                .flatMap { appComponent.signalService.joinRoom(roomId) }
                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .observeOnMainThread()
                .doOnUnsubscribe { hideProgressDialog(TAG_JOIN_ROOM_PROGRESS) }
                .compose(bindUntil(ActivityEvent.STOP))
                .subscribe(object : GlobalSubscriber<Unit>() {
                    override fun onError(e: Throwable) {
                        super.onError(e)
                        appComponent.signalService.quitRoom().subscribeSimple()
                        hideProgressDialog(TAG_JOIN_ROOM_PROGRESS)
                        Toast.makeText(this@RoomActivity, e.describeInHumanMessage(this@RoomActivity), Toast.LENGTH_LONG).show()

                        startActivity(Intent(this@RoomActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                R.anim.slide_in_from_left, R.anim.slide_out_to_right, 0, 0)
                    }

                    override fun onNext(t: Unit) {
                        hideProgressDialog(TAG_JOIN_ROOM_PROGRESS)
                    }
                })
    }

    companion object {
        private const val TAG_JOIN_ROOM_PROGRESS = "tag_join_room_progress"
    }

}
