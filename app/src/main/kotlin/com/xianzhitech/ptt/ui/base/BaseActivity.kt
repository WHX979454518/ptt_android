package com.xianzhitech.ptt.ui.base

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.trello.rxlifecycle.ActivityEvent
import com.trello.rxlifecycle.RxLifecycle
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.repo.optRoomWithMembers
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.dialog.ProgressDialogFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

abstract class BaseActivity : AppCompatActivity(),
        AlertDialogFragment.OnPositiveButtonClickListener,
        AlertDialogFragment.OnNegativeButtonClickListener  {

    private val lifecycleEventSubject = BehaviorSubject.create<ActivityEvent>()
    private var currentInvite : Pair<Room?, Room>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleEventSubject.onNext(ActivityEvent.CREATE)

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_HAS_INVITE_TO_JOIN, false)) {
            onInviteToJoin(intent.getParcelableExtra(EXTRA_CURR_ROOM), intent.getParcelableExtra(EXTRA_REQUESTED_ROOM))
        }
    }

    override fun onDestroy() {
        lifecycleEventSubject.onNext(ActivityEvent.DESTROY)

        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        lifecycleEventSubject.onNext(ActivityEvent.START)
    }

    override fun onStop() {
        lifecycleEventSubject.onNext(ActivityEvent.STOP)

        super.onStop()
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_SWITCH_ROOM_CONFIRMATION -> {
                joinRoom(fragment.attachment as String, confirmed = true)
            }
        }
    }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_SWITCH_ROOM_CONFIRMATION -> fragment.dismissImmediately()
        }
    }

    fun joinRoom(roomId: String, confirmed : Boolean = false) {
        val appComponent = application as AppComponent

        if (confirmed) {
            showProgressDialog(R.string.please_wait, R.string.joining_room, TAG_JOIN_ROOM_PROGRESS)
            ensureConnectivity()
                    .flatMap { appComponent.signalService.joinRoom(roomId) }
                    .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .doOnUnsubscribe { hideProgressDialog(TAG_JOIN_ROOM_PROGRESS) }
                    .compose(bindUntil(ActivityEvent.STOP))
                    .subscribe(object : GlobalSubscriber<Unit>() {
                        override fun onError(e: Throwable) {
                            super.onError(e)
                            appComponent.signalService.quitRoom().subscribeSimple()
                            hideProgressDialog(TAG_JOIN_ROOM_PROGRESS)
                            Toast.makeText(this@BaseActivity, e.describeInHumanMessage(this@BaseActivity), Toast.LENGTH_LONG).show()
                        }

                        override fun onNext(t: Unit) {
                            hideProgressDialog(TAG_JOIN_ROOM_PROGRESS)
                            onRoomJoined()
                        }
                    })
        }
        else {
            Observable.combineLatest(
                    appComponent.roomRepository.optRoomWithMembers(appComponent.signalService.peekRoomState().currentRoomID),
                    appComponent.roomRepository.getRoomWithMembers(roomId).map { it ?: throw StaticUserException(R.string.error_no_such_room) },
                    { currentRoom, requestedRoom -> currentRoom to requestedRoom })
                    .first()
                    .observeOnMainThread()
                    .compose(bindUntil(ActivityEvent.STOP))
                    .doOnError { Toast.makeText(this, it.describeInHumanMessage(this), Toast.LENGTH_LONG).show() }
                    .subscribeSimple {
                        val (currentRoom, requestedRoom) = it
                        if (requestedRoom.id != currentRoom?.id && currentRoom != null) {
                            AlertDialogFragment.Builder().apply {
                                title = R.string.dialog_confirm_title.toFormattedString(this@BaseActivity)
                                message = R.string.room_prompt_switching_message.toFormattedString(this@BaseActivity,
                                        requestedRoom.getRoomName(this@BaseActivity),
                                        currentRoom.getRoomName(this@BaseActivity))
                                btnPositive = R.string.dialog_yes_switch.toFormattedString(this@BaseActivity)
                                btnNegative = R.string.dialog_cancel.toFormattedString(this@BaseActivity)
                                attachment = roomId
                            }.show(supportFragmentManager, TAG_SWITCH_ROOM_CONFIRMATION)
                            supportFragmentManager.executePendingTransactions()
                        } else {
                            joinRoom(roomId, confirmed = true)
                        }
                    }
        }
    }

    protected open fun onRoomJoined() {
        startActivity(Intent(this, RoomActivity::class.java))
    }

    fun joinRoom(createRoomRequest: CreateRoomRequest) {
        val component = application as AppComponent
        val signalService = component.signalService

        showProgressDialog(R.string.please_wait, R.string.getting_room_info, TAG_CREATE_ROOM_PROGRESS)

        signalService.createRoom(createRoomRequest)
                .observeOnMainThread()
                .doOnUnsubscribe { hideProgressDialog(TAG_CREATE_ROOM_PROGRESS) }
                .compose(bindUntil(ActivityEvent.STOP))
                .doOnEach {
                    hideProgressDialog(TAG_CREATE_ROOM_PROGRESS)
                    if (it.hasThrowable()) {
                        Toast.makeText(this@BaseActivity, it.throwable.describeInHumanMessage(this@BaseActivity), Toast.LENGTH_LONG).show()
                    }
                }
                .subscribeSimple { joinRoom(it) }
    }

    fun onInviteToJoin(currRoom : Room?, requestedRoom : Room) {
        if (currentInvite == null || currentInvite!!.second.important.not()) {
            currentInvite = currRoom to requestedRoom

            if (currRoom != null && currRoom.important) {

            }
        }
    }

    private fun showProgressDialog(title : Int, message : Int, tag : String) {
        supportFragmentManager.findFragment<DialogFragment>(tag) ?: ProgressDialogFragment.Builder().apply {
            this.title = title.toFormattedString(this@BaseActivity)
            this.message = message.toFormattedString(this@BaseActivity)
            showImmediately(supportFragmentManager, tag)
        }
    }

    private fun hideProgressDialog(tag : String) {
        supportFragmentManager.findFragment<DialogFragment>(tag)?.dismissImmediately()
    }

    override fun onResume() {
        super.onResume()

        lifecycleEventSubject.onNext(ActivityEvent.RESUME)
    }

    override fun onPause() {
        lifecycleEventSubject.onNext(ActivityEvent.PAUSE)

        super.onPause()
    }

    fun <D> bindToLifecycle(): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindActivity<D>(lifecycleEventSubject)
    }

    fun <D> bindUntil(event: ActivityEvent): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindUntilActivityEvent<D>(lifecycleEventSubject, event)
    }

    companion object {
        private const val TAG_JOIN_ROOM_PROGRESS = "tag_join_room_progress"
        private const val TAG_CREATE_ROOM_PROGRESS = "tag_create_room_progress"
        private const val TAG_SWITCH_ROOM_CONFIRMATION = "tag_switch_room_confirmation"
        private const val TAG_JOIN_INVITED_ROOM_CONFIRMATION = "tag_join_invited_confirmation"

        const val EXTRA_HAS_INVITE_TO_JOIN = "extra_has_invite_to_join"
        const val EXTRA_CURR_ROOM = "extra_curr_room"
        const val EXTRA_REQUESTED_ROOM = "extra_requested_room"
    }
}
