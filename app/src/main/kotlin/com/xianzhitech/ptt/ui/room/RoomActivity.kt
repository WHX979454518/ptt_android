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
import com.xianzhitech.ptt.api.event.WalkieRoomInvitationEvent
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.dismissImmediately
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeActivity
import io.reactivex.CompletableObserver
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
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

            frag.addInvitations(intent.getSerializableExtra(EXTRA_INVITATIONS) as List<WalkieRoomInvitationEvent>)
        }
    }

    override fun showInvitationList(invitations: List<WalkieRoomInvitationEvent>) {
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

        val roomId = intent.getStringExtra(EXTRA_JOIN_ROOM_ID)

        val roomStatus = appComponent.signalBroker.currentWalkieRoomState.map(RoomState::status).distinctUntilChanged().share()

        roomStatus.observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget()
                .subscribe {
                    if (it == RoomStatus.JOINING) {
                        showProgressDialog(R.string.joining_room, TAG_JOIN_ROOM_PROGRESS)
                    } else {
                        hideProgressDialog(TAG_JOIN_ROOM_PROGRESS)
                    }
                }
                .bindToLifecycle()

        roomStatus
                .switchMap {
                    if (it == RoomStatus.IDLE) {
                        Observable.timer(500, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    } else {
                        Observable.never()
                    }
                }
                .logErrorAndForget()
                .subscribe {
                    val peekRoomState = appComponent.signalBroker.currentWalkieRoomState.value
                    if (appComponent.signalBroker.peekUserId() != null && peekRoomState.status == RoomStatus.IDLE && !isFinishing) {
                        Toast.makeText(this, R.string.room_quited, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
                .bindToLifecycle()
    }

    override fun joinRoomConfirmed(roomId: String, fromInvitation: Boolean, isVideoChat: Boolean) {
        val currentRoomId = appComponent.signalBroker.peekWalkieRoomId()
        if (currentRoomId == roomId) {
            return
        }

        if (currentRoomId != null) {
            // Switching room...
            (supportFragmentManager.findFragmentByTag(TAG_INVITATION_LIST_DIALOG) as? DialogFragment)?.dismissImmediately()
        }

        (supportFragmentManager.findFragmentByTag(TAG_INVITE_DIALOG) as? RoomInvitationFragment)?.removeRoomInvitation(roomId)

        appComponent.signalBroker.joinWalkieRoom(roomId, fromInvitation)
                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(JoinRoomSubscriber(applicationContext, roomId))
    }

    private class JoinRoomSubscriber(private val appContext: Context,
                                     private val roomId: String) : CompletableObserver {
        override fun onSubscribe(d: Disposable?) {

        }

        override fun onError(e: Throwable) {
            e.toast()

            val activity = appContext.appComponent.activityProvider.currentStartedActivity
            if (activity is RoomActivity) {
                activity.finish()
            } else {
                appContext.startActivity(Intent(appContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            appContext.appComponent.signalBroker.quitWalkieRoom()
        }

        override fun onComplete() {
            appContext.appComponent.storage.updateRoomLastWalkieActiveTime(roomId).logErrorAndForget().subscribe()
        }
    }

    companion object {
        private const val TAG_JOIN_ROOM_PROGRESS = "tag_join_room_progress"
        private const val TAG_INVITE_DIALOG = "tag_invite_dialog"
        private const val TAG_INVITATION_LIST_DIALOG = "invitation_list_tag"

        const val EXTRA_INVITATIONS = "extra_invitation"
    }

}
