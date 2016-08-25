package com.xianzhitech.ptt.ui.base

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
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
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.AppConfig
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.service.handler.ForceUpdateException
import com.xianzhitech.ptt.ui.PhoneCallHandler
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.dialog.ProgressDialogFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import com.xianzhitech.ptt.update.installPackage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Observable
import rx.SingleSubscriber
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.io.Serializable
import java.util.concurrent.TimeUnit

abstract class BaseActivity : AppCompatActivity(),
        AlertDialogFragment.OnNeutralButtonClickListener,
        AlertDialogFragment.OnPositiveButtonClickListener,
        AlertDialogFragment.OnNegativeButtonClickListener {

    private val lifecycleEventSubject = BehaviorSubject.create<ActivityEvent>()
    private var pendingDeniedPermissions: List<String>? = null
    protected val logger : Logger by lazy { LoggerFactory.getLogger(javaClass.simpleName) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleEventSubject.onNext(ActivityEvent.CREATE)

        if (savedInstanceState == null) {
            handleIntent(intent)
        } else {
            pendingDeniedPermissions = savedInstanceState.getSerializable(STATE_PENDING_DENIED_PERMISSIONS) as? List<String>
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

        lifecycleEventSubject.onNext(ActivityEvent.START)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable(STATE_PENDING_DENIED_PERMISSIONS, pendingDeniedPermissions as? Serializable)
    }

    override fun onStop() {
        lifecycleEventSubject.onNext(ActivityEvent.STOP)

        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        pendingDeniedPermissions = permissions.filterIndexed { i, permission -> grantResults[i] == PackageManager.PERMISSION_DENIED }

        if (pendingDeniedPermissions!!.contains(Manifest.permission.READ_PHONE_STATE).not()) {
            PhoneCallHandler.register(this)
        }
    }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_EXP_NOTIFICATION -> appComponent.preference.lastExpPromptTime = System.currentTimeMillis()
        }
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_PERMISSION_DIALOG -> ActivityCompat.requestPermissions(this, fragment.attachmentAs<List<String>>().toTypedArray(), 0)
            TAG_SWITCH_ROOM_CONFIRMATION -> joinRoomConfirmed(fragment.attachment as String)
            TAG_UPDATE -> startDownload(fragment.attachmentAs<AppConfig>())
        }
    }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_PERMISSION_DIALOG -> finish()
            TAG_SWITCH_ROOM_CONFIRMATION -> fragment.dismissImmediately()
            TAG_UPDATE -> appComponent.preference.lastIgnoredUpdateUrl = fragment.attachmentAs<AppConfig>().downloadUrl
        }
    }

    private fun startDownload(appConfig: AppConfig) {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val downloadUri = Uri.parse(appConfig.downloadUrl)
        val preference = (application as AppComponent).preference
        val lastUpdateDownloadId = preference.updateDownloadId
        if (lastUpdateDownloadId == null || lastUpdateDownloadId.first != downloadUri) {
            if (lastUpdateDownloadId != null) {
                downloadManager.remove(lastUpdateDownloadId.second)
            }

            try {
                val fileName = "${appConfig.getAppFullName(this)}.apk"
                val downloadRequest = DownloadManager.Request(downloadUri)
                downloadRequest.setMimeType("application/vnd.android.package-archive")
                downloadRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                downloadRequest.setNotificationVisibility(View.VISIBLE)
                downloadRequest.setVisibleInDownloadsUi(true)
                downloadRequest.setTitle(fileName)

                preference.updateDownloadId = Pair(downloadUri, downloadManager.enqueue(downloadRequest))
            } catch(e: Exception) {
                logger.e(e) { "Download failed: " }
                Toast.makeText(this, R.string.error_download, Toast.LENGTH_LONG).show()
            }
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

        signalService.createRoom(createRoomRequest.groupIds, createRoomRequest.extraMemberIds)
                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(CreateRoomSubscriber(applicationContext))
    }

    fun onLoginError(error: Throwable) {
        if (error is ForceUpdateException) {
            handleUpdate(error.appParams)
        } else {
            Toast.makeText(this, error.describeInHumanMessage(this), Toast.LENGTH_LONG).show()
        }
    }

    fun handleUpdate(appParams: AppConfig) {
        if (appParams.hasUpdate &&
                appParams.downloadUrl.isNullOrBlank().not() &&
                (appParams.mandatory == true || appComponent.preference.lastIgnoredUpdateUrl != appParams.downloadUrl) &&
                appComponent.preference.updateDownloadId == null) {
            AlertDialogFragment.Builder().apply {
                message = appParams.updateMessage
                messageIsHtml = true
                title = R.string.update_title.toFormattedString(this@BaseActivity, appParams.getAppFullVersionName())
                btnPositive = R.string.update.toFormattedString(this@BaseActivity)
                btnNegative = if (appParams.mandatory) null else R.string.ignore.toFormattedString(this@BaseActivity)
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


        if (pendingDeniedPermissions?.isNotEmpty() ?: false) {
            AlertDialogFragment.Builder().apply {
                message = R.string.error_no_android_permissions.toFormattedString(this@BaseActivity)
                btnPositive = R.string.dialog_confirm.toFormattedString(this@BaseActivity)
                btnNegative = R.string.dialog_cancel.toFormattedString(this@BaseActivity)
                attachment = pendingDeniedPermissions as Serializable
            }.show(supportFragmentManager, TAG_PERMISSION_DIALOG)

            pendingDeniedPermissions = null
        } else {
            val permissionsToRequest = ALL_PERMISSIONS.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (permissionsToRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 0)
            }

            if (permissionsToRequest.contains(Manifest.permission.READ_PHONE_STATE).not()) {
                PhoneCallHandler.register(this)
            }
        }

        appComponent.appService.retrieveAppConfig(appComponent.signalHandler.currentUserId ?: Constants.EMPTY_USER_ID)
                .toObservable()
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    handleUpdate(it)
                }

        appComponent.signalHandler.currentUserIdSubject
            .switchMap { appComponent.userRepository.getUser(it).getAsync().toObservable() }
            .observeOnMainThread()
            .compose(bindToLifecycle())
            .subscribeSimple {
                val now = System.currentTimeMillis()
                if (it != null && it.enterpriseExpireDate != null &&
                        it.enterpriseExpireDate!!.time >= now &&
                        it.enterpriseExpireDate!!.time - now < Constants.EXP_TIME_PROMPT_ADVANCE_MILLSECONDS &&
                        (appComponent.preference.lastExpPromptTime == null || now - appComponent.preference.lastExpPromptTime!! >= Constants.PROMPT_EXP_TIME_INTERVAL_MILLSECONDS)) {
                    AlertDialogFragment.Builder().apply {
                        val expDays = Math.ceil((it.enterpriseExpireDate!!.time - now).toDouble() / TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)).toInt()
                        message = R.string.trial_exp_in_days.toFormattedString(this@BaseActivity, expDays)
                        btnNeutral = R.string.dialog_ok.toFormattedString(this@BaseActivity)
                    }.show(supportFragmentManager, TAG_EXP_NOTIFICATION)

                    supportFragmentManager.executePendingTransactions()
                }
            }
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
            defaultOnErrorAction.call(error)

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
        const val TAG_CREATE_ROOM_PROGRESS = "tag_create_room_progress"
        const val TAG_SWITCH_ROOM_CONFIRMATION = "tag_switch_room_confirmation"
        const val TAG_EXP_NOTIFICATION = "tag_exp_notification"
        const val TAG_UPDATE = "tag_update"
        private const val TAG_PERMISSION_DIALOG = "tag_permission"

        const val EXTRA_FINISH_ENTER_ANIM = "extra_f_enter_ani"
        const val EXTRA_FINISH_EXIT_ANIM = "extra_f_exit_ani"

        const val EXTRA_JOIN_ROOM_ID = "extra_jri"
        const val EXTRA_JOIN_ROOM_CONFIRMED = "extra_jrc"

        private const val STATE_PENDING_DENIED_PERMISSIONS = "state_pending_denied_permissions"

        private val ALL_PERMISSIONS = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun startActivityJoiningRoom(context: Context, activity: Class<*>, roomId: String) {
            context.startActivity(Intent(context, activity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, roomId)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true))

        }
    }


}
