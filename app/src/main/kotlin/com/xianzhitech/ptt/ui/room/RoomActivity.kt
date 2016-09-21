package com.xianzhitech.ptt.ui.room

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentTransaction
import android.view.WindowManager
import android.widget.Toast
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.KnownServerException
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.RoomInvitation
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.ui.MainActivity
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity
import rx.CompletableSubscriber
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

/**
 * 房间对话界面

 * Created by fanchao on 11/12/15.
 */
class RoomActivity : BaseActivity(), RoomFragment.Callbacks, RoomInvitationFragment.Callbacks, RoomInvitationListFragment.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

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
        var roomFragment = supportFragmentManager.findFragmentById(R.id.room_content) as? RoomFragment
        if (roomFragment == null) {
            roomFragment = RoomFragment()
            supportFragmentManager.beginTransaction()
                    .replace(R.id.room_content, roomFragment)
                    .commit()
            supportFragmentManager.executePendingTransactions()
        }

        if (intent.hasExtra(EXTRA_INVITATIONS)) {
            var frag = supportFragmentManager.findFragmentByTag(TAG_INVITE_DIALOG) as? RoomInvitationFragment

            if (frag == null) {
                frag = RoomInvitationFragment()
                supportFragmentManager.beginTransaction()
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .replace(R.id.room_invitationContainer, frag, TAG_INVITE_DIALOG)
                        .commit()
            }

            frag.addInvitations(intent.getSerializableExtra(EXTRA_INVITATIONS) as List<RoomInvitation>)
        }
    }

    override fun showInvitationList(invitations: List<RoomInvitation>) {
        RoomInvitationListFragment.build(invitations).show(supportFragmentManager, TAG_INVITATION_LIST_DIALOG)
        supportFragmentManager.executePendingTransactions()
    }

    override fun ignoreAllInvitations(from: Fragment?) {
        (from as? DialogFragment)?.dismissImmediately()
        (supportFragmentManager.findFragmentByTag(TAG_INVITE_DIALOG) as? RoomInvitationFragment)?.ignoreAllInvitations()
    }

    override fun dismissInvitations() {
        supportFragmentManager.findFragmentByTag(TAG_INVITE_DIALOG)?.let {
            supportFragmentManager.beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .remove(it)
                    .commit()
            supportFragmentManager.executePendingTransactions()
        }
    }

    override fun onBackPressed() {
        supportFragmentManager.findFragmentById(R.id.room_content)?.let {
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

        appComponent.signalHandler
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

        appComponent.signalHandler.roomStatus
                .switchMap {
                    if (it == RoomStatus.IDLE) {
                        rx.Observable.timer(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    } else {
                        rx.Observable.never()
                    }
                }
                .bindToLifecycle()
                .subscribeSimple {
                    val loginStatus = appComponent.signalHandler.peekLoginStatus()
                    val peekRoomState = appComponent.signalHandler.peekRoomState()
                    if (loginStatus == LoginStatus.LOGGED_IN && peekRoomState.status == RoomStatus.IDLE && !isFinishing) {
                        Toast.makeText(this, R.string.room_quited, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
    }

    override fun joinRoomConfirmed(roomId: String, fromInvitation : Boolean) {
        val currentRoomId = appComponent.signalHandler.peekCurrentRoomId()
        if (currentRoomId == roomId) {
            return
        }

        if (currentRoomId != null) {
            // Switching room...
            (supportFragmentManager.findFragmentByTag(TAG_INVITATION_LIST_DIALOG) as? DialogFragment)?.dismissImmediately()
        }

        (supportFragmentManager.findFragmentByTag(TAG_INVITE_DIALOG) as? RoomInvitationFragment)?.removeRoomInvitation(roomId)

        appComponent.signalHandler.joinRoom(roomId, fromInvitation)
                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(JoinRoomSubscriber(applicationContext, roomId))
    }

    private class JoinRoomSubscriber(private val appContext: Context,
                                     private val roomId: String) : CompletableSubscriber {
        override fun onSubscribe(d: Subscription?) {
        }

        override fun onError(e: Throwable) {
            if (e is KnownServerException && e.errorName == "no_initiator") {
                //这个错误表明这个房间邀请已经失效，不需要提示什么
            }
            else {
                defaultOnErrorAction.call(e)
            }

            val activity = appContext.appComponent.activityProvider.currentStartedActivity
            if (activity is RoomActivity) {
                activity.finish()
            } else {
                appContext.startActivity(Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            appContext.appComponent.signalHandler.quitRoom()
        }

        override fun onCompleted() {
            appContext.appComponent.roomRepository.updateLastRoomActiveTime(roomId).execAsync().subscribeSimple()
        }
    }

    companion object {
        private const val TAG_JOIN_ROOM_PROGRESS = "tag_join_room_progress"
        private const val TAG_INVITE_DIALOG = "tag_invite_dialog"
        private const val TAG_INVITATION_LIST_DIALOG = "invitation_list_tag"

        const val EXTRA_INVITATIONS = "extra_invitation"
    }

}
