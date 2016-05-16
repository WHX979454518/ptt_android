package com.xianzhitech.ptt.ui.base

import android.content.Context
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
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.dismissImmediately
import com.xianzhitech.ptt.ext.ensureConnectivity
import com.xianzhitech.ptt.ext.findFragment
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.dialog.ProgressDialogFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import rx.Observable
import rx.Single
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

abstract class BaseActivity : AppCompatActivity(),
        AlertDialogFragment.OnPositiveButtonClickListener,
        AlertDialogFragment.OnNegativeButtonClickListener  {

    private val lifecycleEventSubject = BehaviorSubject.create<ActivityEvent>()

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
        intent.getStringExtra(EXTRA_JOIN_ROOM_ID)?.let { roomId ->
            joinRoom(roomId, confirmed = intent.getBooleanExtra(EXTRA_JOIN_ROOM_CONFIRMED, false))
            intent.removeExtra(EXTRA_JOIN_ROOM_ID)
            intent.removeExtra(EXTRA_JOIN_ROOM_CONFIRMED)
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
            TAG_SWITCH_ROOM_CONFIRMATION -> joinRoom(fragment.attachment as String, confirmed = true)
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
                    .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    .observeOnMainThread()
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
            Single.zip(
                    appComponent.roomRepository.getRoom(appComponent.signalService.peekRoomState().currentRoomID).getAsync(),
                    appComponent.roomRepository.getRoom(roomId).getAsync().map { it ?: throw StaticUserException(R.string.error_no_such_room) },
                    { currentRoom, requestedRoom -> currentRoom to requestedRoom })
                    .toObservable()
                    .observeOnMainThread()
                    .compose(bindUntil(ActivityEvent.STOP))
                    .doOnError { Toast.makeText(this, it.describeInHumanMessage(this), Toast.LENGTH_LONG).show() }
                    .subscribeSimple {
                        val (currentRoom, requestedRoom) = it
                        if (requestedRoom.id != currentRoom?.id && currentRoom != null) {
                            AlertDialogFragment.Builder().apply {
                                title = R.string.dialog_confirm_title.toFormattedString(this@BaseActivity)
                                message = R.string.room_prompt_switching_message.toFormattedString(this@BaseActivity)
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
        startActivity(Intent(this, RoomActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
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

    private fun showProgressDialog(title : Int, message : Int, tag : String) {
        supportFragmentManager.findFragment<DialogFragment>(tag) ?: ProgressDialogFragment.Builder().apply {
            this.title = title.toFormattedString(this@BaseActivity)
            this.message = message.toFormattedString(this@BaseActivity)
            showImmediately(supportFragmentManager, tag)
        }
    }

    override fun finish() {
        super.finish()

        if (intent.hasExtra(EXTRA_FINISH_ENTER_ANIM) && intent.hasExtra(EXTRA_FINISH_EXIT_ANIM)) {
            overridePendingTransition(intent.getIntExtra(EXTRA_FINISH_ENTER_ANIM, 0), intent.getIntExtra(EXTRA_FINISH_EXIT_ANIM, 0))
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
        return RxLifecycle.bindActivity(lifecycleEventSubject)
    }

    fun <D> bindUntil(event: ActivityEvent): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindUntilEvent(lifecycleEventSubject, event)
    }

    protected fun <T> Observable<T>.bindToLifecycle() : Observable<T> {
        return compose(RxLifecycle.bindActivity<T>(lifecycleEventSubject))
    }

    protected fun <T> Observable<T>.bindUntil(event : ActivityEvent) : Observable<T> {
        return compose(this@BaseActivity.bindUntil(event))
    }

    companion object {
        private const val TAG_JOIN_ROOM_PROGRESS = "tag_join_room_progress"
        private const val TAG_CREATE_ROOM_PROGRESS = "tag_create_room_progress"
        private const val TAG_SWITCH_ROOM_CONFIRMATION = "tag_switch_room_confirmation"

        const val EXTRA_FINISH_ENTER_ANIM = "extra_f_enter_ani"
        const val EXTRA_FINISH_EXIT_ANIM = "extra_f_exit_ani"

        const val EXTRA_JOIN_ROOM_ID = "extra_jri"
        const val EXTRA_JOIN_ROOM_CONFIRMED = "extra_jrc"

        fun startActivityJoiningRoom(context: Context, activity : Class<*>, roomId: String) {
            context.startActivity(Intent(context, activity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, roomId)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true))

        }
    }
}
