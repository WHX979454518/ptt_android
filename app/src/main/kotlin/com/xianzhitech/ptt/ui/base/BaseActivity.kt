package com.xianzhitech.ptt.ui.base

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import com.trello.rxlifecycle.ActivityEvent
import com.trello.rxlifecycle.RxLifecycle
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.media.MediaButtonReceiver
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.AppParams
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.service.handler.ForceUpdateException
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.dialog.ProgressDialogFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import com.xianzhitech.ptt.update.installPackage
import rx.Observable
import rx.SingleSubscriber
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

abstract class BaseActivity : AppCompatActivity(),
        AlertDialogFragment.OnPositiveButtonClickListener,
        AlertDialogFragment.OnNegativeButtonClickListener,
        AlertDialogFragment.OnDismissListener{

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
            if (intent.getBooleanExtra(EXTRA_JOIN_ROOM_CONFIRMED, false)) {
                joinRoomConfirmed(roomId)
            } else {
                joinRoom(roomId)
            }
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

        // 时刻重新注册媒体按键事件
        if ((application as AppComponent).signalHandler.currentUserId != null) {
            MediaButtonReceiver.registerMediaButtonEvent(this)
        }

        lifecycleEventSubject.onNext(ActivityEvent.START)


        appComponent.appService.retrieveAppParams()
                .subscribeOn(Schedulers.io())
                .toObservable()
                .compose(bindToLifecycle())
                .subscribe(object : GlobalSubscriber<AppParams>() {
                    override fun onNext(t: AppParams) {
                        handleUpdate(t)
                    }
                })
    }

    override fun onStop() {
        lifecycleEventSubject.onNext(ActivityEvent.STOP)

        super.onStop()
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_SWITCH_ROOM_CONFIRMATION -> joinRoomConfirmed(fragment.attachment as String)
            TAG_UPDATE -> startDownload(fragment.attachmentAs<AppParams>().updateUrl!!)
        }
    }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_SWITCH_ROOM_CONFIRMATION -> fragment.dismissImmediately()
            TAG_UPDATE -> onDismiss(fragment)
        }
    }

    override fun onDismiss(fragment: AlertDialogFragment) {
        if (fragment.tag == TAG_UPDATE) {
            if (fragment.attachmentAs<AppParams>().forceUpdate == true) {
                finish()
                return
            }

            appComponent.preference.lastIgnoredUpdateUrl = fragment.attachmentAs<AppParams>().updateUrl
        }
    }

    private fun startDownload(updateUrl: String) {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val downloadUri = Uri.parse(updateUrl)
        val preference = (application as AppComponent).preference
        val lastUpdateDownloadId = preference.updateDownloadId
        if (lastUpdateDownloadId == null || lastUpdateDownloadId.first != downloadUri) {
            if (lastUpdateDownloadId != null) {
                downloadManager.remove(lastUpdateDownloadId.second)
            }

            val downloadRequest = DownloadManager.Request(downloadUri).apply {
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(View.VISIBLE)
                setVisibleInDownloadsUi(false)
                setTitle(R.string.app_updating.toFormattedString(this@BaseActivity, R.string.app_name.toFormattedString(this@BaseActivity)))
            }
            preference.updateDownloadId = Pair(downloadUri, downloadManager.enqueue(downloadRequest))
        } else {
            installPackage(this, lastUpdateDownloadId.second)
        }
    }

    fun joinRoom(roomId: String) {
        val appComponent = application as AppComponent

        val currentRoomID = appComponent.signalHandler.peekRoomState().currentRoomId

        // 如果用户已经加入这个房间, 直接确认这个操作
        if (currentRoomID == roomId) {
            joinRoomConfirmed(roomId)
            return
        }

        // 如果用户已经加入另外一个房间, 需要提示
        if (currentRoomID != null) {
            AlertDialogFragment.Builder().apply {
                title = R.string.dialog_confirm_switch_title.toFormattedString(this@BaseActivity)
                message = R.string.room_prompt_switching_message.toFormattedString(this@BaseActivity)
                btnPositive = R.string.dialog_yes_switch.toFormattedString(this@BaseActivity)
                btnNegative = R.string.dialog_cancel.toFormattedString(this@BaseActivity)
                attachment = roomId

                show(supportFragmentManager, TAG_SWITCH_ROOM_CONFIRMATION)
            }
            supportFragmentManager.executePendingTransactions()
            return
        }

        // 如果用户没有加入任意一个房间, 则确认操作
        joinRoomConfirmed(roomId)
    }

    open fun joinRoomConfirmed(roomId: String) {
        // Base 类不知道具体怎么加入房间, 打开RoomActivity来加入房间
        startActivityWithAnimation(
                Intent(this, RoomActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                        .putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, roomId)
                        .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true),
                R.anim.slide_in_from_right, R.anim.slide_out_to_left, R.anim.slide_in_from_left, R.anim.slide_out_to_right)
    }

    fun joinRoom(createRoomRequest: CreateRoomRequest) {
        val component = application as AppComponent
        val signalService = component.signalHandler

        showProgressDialog(R.string.getting_room_info, TAG_CREATE_ROOM_PROGRESS)

        signalService.createRoom(createRoomRequest)
                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(CreateRoomSubscriber(applicationContext))
    }

    fun onLoginError(error: Throwable) {
        if (error is ForceUpdateException) {
            handleUpdate(error.appParams)
        }
        else {
            Toast.makeText(this, error.describeInHumanMessage(this), Toast.LENGTH_LONG).show()
        }
    }

    fun handleUpdate(appParams: AppParams) {
        if (appParams.updateUrl.isNullOrEmpty().not() &&
                (appParams.forceUpdate == true || appComponent.preference.lastIgnoredUpdateUrl != appParams.updateUrl)) {
            AlertDialogFragment.Builder().apply {
                message = appParams.updateMessage
                title = R.string.update_title.toFormattedString(this@BaseActivity)
                btnPositive = R.string.update.toFormattedString(this@BaseActivity)
                btnNegative = if (appParams.forceUpdate == true) null else R.string.ignore.toFormattedString(this@BaseActivity)
                cancellabe = false
                attachment = appParams
            }.show(supportFragmentManager, TAG_UPDATE)
        }
    }

    protected fun showProgressDialog(message: Int, tag: String) {
        supportFragmentManager.findFragment<DialogFragment>(tag) ?: ProgressDialogFragment.Builder().apply {
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

    protected fun hideProgressDialog(tag: String) {
        supportFragmentManager.findFragment<DialogFragment>(tag)?.dismissImmediately()
    }

    override fun onResume() {
        super.onResume()

        lifecycleEventSubject.onNext(ActivityEvent.RESUME)
    }

    override fun onPause() {
        lifecycleEventSubject.onNext(ActivityEvent.PAUSE)
        hideProgressDialog(TAG_CREATE_ROOM_PROGRESS)

        super.onPause()
    }

    fun <D> bindToLifecycle(): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindActivity(lifecycleEventSubject)
    }

    fun <D> bindUntil(event: ActivityEvent): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindUntilEvent(lifecycleEventSubject, event)
    }

    protected fun <T> Observable<T>.bindToLifecycle(): Observable<T> {
        return compose(RxLifecycle.bindActivity<T>(lifecycleEventSubject))
    }

    protected fun <T> Observable<T>.bindUntil(event: ActivityEvent): Observable<T> {
        return compose(this@BaseActivity.bindUntil(event))
    }

    private class CreateRoomSubscriber(private val appContext: Context) : SingleSubscriber<Room>() {
        override fun onError(error: Throwable) {
            globalHandleError(error, appContext)

            ((appContext as AppComponent).activityProvider.currentStartedActivity as? BaseActivity)?.hideProgressDialog(TAG_CREATE_ROOM_PROGRESS)
        }

        override fun onSuccess(value: Room) {
            val currentActivity = (appContext as AppComponent).activityProvider.currentStartedActivity as? BaseActivity
            if (currentActivity != null) {
                currentActivity.hideProgressDialog(TAG_CREATE_ROOM_PROGRESS)
                currentActivity.joinRoom(value.id)
            } else {
                startActivityJoiningRoom(appContext, RoomActivity::class.java, value.id)
            }
        }
    }

    companion object {
        private const val TAG_CREATE_ROOM_PROGRESS = "tag_create_room_progress"
        private const val TAG_SWITCH_ROOM_CONFIRMATION = "tag_switch_room_confirmation"
        private const val TAG_UPDATE = "tag_update"

        const val EXTRA_FINISH_ENTER_ANIM = "extra_f_enter_ani"
        const val EXTRA_FINISH_EXIT_ANIM = "extra_f_exit_ani"

        const val EXTRA_JOIN_ROOM_ID = "extra_jri"
        const val EXTRA_JOIN_ROOM_CONFIRMED = "extra_jrc"

        fun startActivityJoiningRoom(context: Context, activity: Class<*>, roomId: String) {
            context.startActivity(Intent(context, activity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, roomId)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true))

        }
    }


}
