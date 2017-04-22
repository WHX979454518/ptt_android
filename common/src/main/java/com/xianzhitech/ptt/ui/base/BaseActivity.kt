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
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.api.dto.AppConfig
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.PhoneCallHandler
import com.xianzhitech.ptt.ui.call.CallActivity
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.dialog.ProgressDialogFragment
import com.xianzhitech.ptt.ui.room.RoomActivity
import com.xianzhitech.ptt.update.installPackage
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.util.concurrent.TimeUnit

abstract class BaseActivity : AppCompatActivity(),
        AlertDialogFragment.OnNeutralButtonClickListener,
        AlertDialogFragment.OnPositiveButtonClickListener,
        AlertDialogFragment.OnNegativeButtonClickListener {

    private var pendingDeniedPermissions: List<String>? = null
    protected val logger: Logger by lazy { LoggerFactory.getLogger(javaClass.simpleName) }
    private var disposables: CompositeDisposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            handleIntent(intent)
        } else {
            pendingDeniedPermissions = savedInstanceState.getSerializable(STATE_PENDING_DENIED_PERMISSIONS) as? List<String>
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_JOIN_ROOM_ID)?.let { roomId ->
            val fromInvitation = intent.getBooleanExtra(EXTRA_JOIN_ROOM_FROM_INVITATION, false)
            if (intent.getBooleanExtra(EXTRA_JOIN_ROOM_CONFIRMED, false)) {
                joinRoomConfirmed(roomId, fromInvitation, intent.getBooleanExtra(EXTRA_JOIN_ROOM_IS_VIDEO_CHAT, false))
            } else {
                joinRoom(roomId, fromInvitation, intent.getBooleanExtra(EXTRA_JOIN_ROOM_IS_VIDEO_CHAT, false))
            }
            intent.removeExtra(EXTRA_JOIN_ROOM_ID)
            intent.removeExtra(EXTRA_JOIN_ROOM_CONFIRMED)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable(STATE_PENDING_DENIED_PERMISSIONS, pendingDeniedPermissions as? Serializable)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        pendingDeniedPermissions = permissions.filterIndexed { i, _ -> grantResults[i] == PackageManager.PERMISSION_DENIED }

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
            TAG_SWITCH_ROOM_CONFIRMATION -> {
                val (roomId, fromInvitation, isVideoChat) = (fragment.attachment as JoinRoomBundle)
                joinRoomConfirmed(roomId, fromInvitation, isVideoChat)
            }
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

    fun navigateToWalkieTalkiePage(roomId: String) {
        joinRoom(roomId, fromInvitation = false)
    }

    fun navigateToWalkieTalkiePage() {
        startActivityWithAnimation(Intent(this, RoomActivity::class.java))
    }

    fun navigateToVideoChatPage(roomId: String) {
        joinRoom(roomId, fromInvitation = false, isVideoChat = true)
    }

    fun navigateToVideoChatPage() {
        startActivityWithAnimation(Intent(this, CallActivity::class.java))
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

    fun joinRoom(roomId: String, fromInvitation: Boolean, isVideoChat: Boolean = false) {
        val appComponent = application as AppComponent

        val currentRoomID = appComponent.signalBroker.peekWalkieRoomId()

        if ((isVideoChat && currentRoomID != null) || (currentRoomID != roomId && currentRoomID != null)) {
            AlertDialogFragment.Builder().apply {
                title = R.string.dialog_confirm_switch_title.toFormattedString(this@BaseActivity)
                message = R.string.room_prompt_switching_message.toFormattedString(this@BaseActivity)
                btnPositive = R.string.dialog_yes_switch.toFormattedString(this@BaseActivity)
                btnNegative = R.string.dialog_cancel.toFormattedString(this@BaseActivity)
                attachment = JoinRoomBundle(roomId, fromInvitation, isVideoChat)

                show(supportFragmentManager, TAG_SWITCH_ROOM_CONFIRMATION)
            }
            supportFragmentManager.executePendingTransactions()
            return
        }

        // 如果用户已经加入这个房间, 直接确认这个操作
        // 如果用户没有加入任意一个房间, 则确认操作
        joinRoomConfirmed(roomId, fromInvitation, isVideoChat)
    }

    open fun joinRoomConfirmed(roomId: String, fromInvitation: Boolean, isVideoChat: Boolean) {
        val intent = if (isVideoChat) Intent(this, CallActivity::class.java) else Intent(this, RoomActivity::class.java)

        // Base 类不知道具体怎么加入房间, 打开Activity来加入房间
        startActivityWithAnimation(intent
                .addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                .putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, roomId)
                .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true),
                R.anim.slide_in_from_right, R.anim.slide_out_to_left, R.anim.slide_in_from_left, R.anim.slide_out_to_right)
    }

    fun joinRoom(groupIds: List<String> = emptyList(),
                 userIds: List<String> = emptyList(), isVideoChat: Boolean = false) {
        val component = application as AppComponent
        val signalService = component.signalBroker

        showProgressDialog(R.string.getting_room_info, TAG_CREATE_ROOM_PROGRESS)

        signalService.createRoom(userIds = userIds, groupIds = groupIds)
                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(CreateRoomSubscriber(applicationContext, isVideoChat))
    }

    fun navigateToDialPhone(phoneNumber: String) {
        startActivity(Intent(Intent.ACTION_DIAL).setData(Uri.parse("tel:$phoneNumber")))
    }

    fun onLoginError(error: Throwable) {
        Toast.makeText(this, error.describeInHumanMessage(this), Toast.LENGTH_LONG).show()
    }

    fun handleUpdate(appParams: AppConfig) {
        if (appParams.hasUpdate(appComponent.currentVersion) &&
                appParams.downloadUrl.isNullOrBlank().not() &&
                (appParams.mandatory || appComponent.preference.lastIgnoredUpdateUrl != appParams.downloadUrl) &&
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

        appComponent.appApi.retrieveAppConfig(appComponent.signalBroker.peekUserId() ?: "", appComponent.currentVersion.toString())
                .toMaybe()
                .logErrorAndForget()
                .subscribe(this::handleUpdate)
                .bindToLifecycle()

        appComponent.signalBroker.currentUser
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget()
                .subscribe { it ->
                    val user = it.orNull()
                    val now = System.currentTimeMillis()
                    if (user != null && user.enterpriseExpireTime != null &&
                            user.enterpriseExpireTime >= now &&
                            user.enterpriseExpireTime - now < Constants.EXP_TIME_PROMPT_ADVANCE_MILLSECONDS &&
                            (appComponent.preference.lastExpPromptTime == null || now - appComponent.preference.lastExpPromptTime!! >= Constants.PROMPT_EXP_TIME_INTERVAL_MILLSECONDS)) {
                        AlertDialogFragment.Builder().apply {
                            val expDays = Math.ceil((user.enterpriseExpireTime - now).toDouble() / TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)).toInt()
                            message = R.string.trial_exp_in_days.toFormattedString(this@BaseActivity, expDays)
                            btnNeutral = R.string.dialog_ok.toFormattedString(this@BaseActivity)
                        }.show(supportFragmentManager, TAG_EXP_NOTIFICATION)

                        supportFragmentManager.executePendingTransactions()
                    }
                }
                .bindToLifecycle()
    }


    override fun onStop() {
        super.onStop()

        disposables?.dispose()
        disposables = null
    }

    protected fun Disposable.bindToLifecycle(): Disposable {
        if (disposables == null) {
            disposables = CompositeDisposable()
        }

        disposables!!.add(this)
        return this
    }

    private inner class CreateRoomSubscriber(private val appContext: Context, private val isVideoChat: Boolean) : SingleObserver<Room> {
        override fun onSubscribe(d: Disposable?) {

        }

        override fun onError(error: Throwable) {
            error.toast()
            hideProgressDialog(TAG_CREATE_ROOM_PROGRESS)
        }

        override fun onSuccess(value: Room) {
            hideProgressDialog(TAG_CREATE_ROOM_PROGRESS)
            val currentActivity = (appContext as AppComponent).activityProvider.currentStartedActivity as? BaseActivity
            if (currentActivity != null) {
                currentActivity.joinRoom(value.id, false, isVideoChat)
            } else {
                startActivityJoiningRoom(appContext, RoomActivity::class.java, value.id)
            }
        }
    }

    private data class JoinRoomBundle(val roomId: String,
                                      val fromInvitation: Boolean,
                                      val isVideoChat: Boolean) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
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
        const val EXTRA_JOIN_ROOM_FROM_INVITATION = "extra_fi"
        const val EXTRA_JOIN_ROOM_IS_VIDEO_CHAT = "extra_isv"

        private const val STATE_PENDING_DENIED_PERMISSIONS = "state_pending_denied_permissions"

        private val ALL_PERMISSIONS = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
        )

        fun startActivityJoiningRoom(context: Context, activity: Class<*>, roomId: String) {
            context.startActivity(Intent(context, activity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_ID, roomId)
                    .putExtra(BaseActivity.EXTRA_JOIN_ROOM_CONFIRMED, true))

        }
    }


}