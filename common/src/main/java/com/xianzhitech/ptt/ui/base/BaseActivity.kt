package com.xianzhitech.ptt.ui.base

import android.Manifest
import android.app.DownloadManager
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
import com.xianzhitech.ptt.api.dto.AppConfig
import com.xianzhitech.ptt.api.event.WalkieRoomInvitationEvent
import com.xianzhitech.ptt.broker.RoomMode
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.PhoneCallHandler
import com.xianzhitech.ptt.ui.call.CallActivity
import com.xianzhitech.ptt.ui.call.CallFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.dialog.ProgressDialogFragment
import com.xianzhitech.ptt.ui.map.MapFragment
import com.xianzhitech.ptt.ui.room.RoomMemberListFragment
import com.xianzhitech.ptt.ui.user.UserDetailsFragment
import com.xianzhitech.ptt.ui.walkie.WalkieRoomActivity
import com.xianzhitech.ptt.ui.walkie.WalkieRoomFragment
import com.xianzhitech.ptt.update.installPackage
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.util.*
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
            handleJoinRoomIntent(intent)
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
        handleJoinRoomIntent(intent)
    }

    open fun handleJoinRoomIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_NAVIGATE_TO_WALKIE, false)) {
            navigateToWalkieTalkiePage()
            return
        }

        intent.getStringExtra(EXTRA_JOIN_ROOM_ID)?.let { roomId ->
            val fromInvitation = intent.getBooleanExtra(EXTRA_JOIN_ROOM_FROM_INVITATION, false)
            if (intent.getBooleanExtra(EXTRA_JOIN_ROOM_CONFIRMED, false)) {
                joinRoomConfirmed(
                        roomId = roomId,
                        fromInvitation = fromInvitation,
                        roomMode = intent.getSerializableExtra(EXTRA_JOIN_ROOM_MODE) as? RoomMode ?: RoomMode.NORMAL
                )
            } else {
                joinRoom(
                        roomId = roomId,
                        fromInvitation = fromInvitation,
                        roomMode = intent.getSerializableExtra(EXTRA_JOIN_ROOM_MODE) as? RoomMode ?: RoomMode.NORMAL
                )
            }
            intent.removeExtra(EXTRA_JOIN_ROOM_ID)
            intent.removeExtra(EXTRA_JOIN_ROOM_CONFIRMED)
        }

        intent.getParcelableArrayListExtra<WalkieRoomInvitationEvent>(EXTRA_PENDING_INVITATION)?.let(this::onNewPendingInvitation)
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
        appComponent.signalBroker.peekWalkieRoomId()?.let { joinRoom(it, fromInvitation = false) } ?: logger.e { "No walkie room to go to" }
    }

    open fun navigateToVideoChatPage(roomId: String, audioOnly: Boolean) {
        joinRoom(roomId, fromInvitation = false, roomMode = if (audioOnly) RoomMode.AUDIO else RoomMode.VIDEO)
    }

    fun navigateToVideoChatPage() {
        appComponent.signalBroker.peekVideoRoomId()?.let {
            navigateToVideoChatPage(it, appComponent.signalBroker.videoChatVideoOn.not())
        }
    }

    open fun navigateToRoomMemberPage(roomId: String) {
        startActivityWithAnimation(
                FragmentDisplayActivity.createIntent(
                        RoomMemberListFragment::class.java,
                        RoomMemberListFragment.ARG_ROOM_ID,
                        roomId
                )
        )
    }

    open fun navigateToUserDetailsPage(userId: String) {
        startActivityWithAnimation(
                FragmentDisplayActivity.createIntent(
                        UserDetailsFragment::class.java,
                        UserDetailsFragment.ARG_USER_ID,
                        userId
                )
        )
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
                val fileName = getString(R.string.app_name) + "-${appConfig.latestVersionName}.apk"
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

    fun joinRoom(roomId: String, fromInvitation: Boolean, roomMode: RoomMode = RoomMode.NORMAL) {
        val appComponent = application as AppComponent

        val currentRoomID = appComponent.signalBroker.peekWalkieRoomId()

        if (((roomMode == RoomMode.AUDIO || roomMode == RoomMode.VIDEO) && currentRoomID != null) ||
                (currentRoomID != roomId && currentRoomID != null)) {
            AlertDialogFragment.Builder().apply {
                title = R.string.dialog_confirm_switch_title.toFormattedString(this@BaseActivity)
                message = R.string.room_prompt_switching_message.toFormattedString(this@BaseActivity)
                btnPositive = R.string.dialog_yes_switch.toFormattedString(this@BaseActivity)
                btnNegative = R.string.dialog_cancel.toFormattedString(this@BaseActivity)
                attachment = JoinRoomBundle(roomId, fromInvitation, roomMode)

                show(supportFragmentManager, TAG_SWITCH_ROOM_CONFIRMATION)
            }
            supportFragmentManager.executePendingTransactions()
            return
        }

        // 如果用户已经加入这个房间, 直接确认这个操作
        // 如果用户没有加入任意一个房间, 则确认操作
        joinRoomConfirmed(roomId, fromInvitation, roomMode)
    }

    open fun joinRoomConfirmed(roomId: String, fromInvitation: Boolean, roomMode: RoomMode) {
        val intent: Intent

        when (roomMode) {
            RoomMode.EMERGENCY -> TODO()
            RoomMode.BROADCAST -> TODO()
            RoomMode.SYSTEM_CALL -> TODO()
            RoomMode.NORMAL -> {
                intent = Intent(this, WalkieRoomActivity::class.java)
                        .putExtra(WalkieRoomFragment.ARG_REQUEST_JOIN_ROOM_ID, roomId)
                        .putExtra(WalkieRoomFragment.ARG_REQUEST_JOIN_ROOM_FROM_INVITATION, fromInvitation)
            }

            RoomMode.VIDEO, RoomMode.AUDIO -> {
                intent = Intent(this, CallActivity::class.java)
                        .putExtra(CallFragment.ARG_JOIN_ROOM_ID, roomId)
                        .putExtra(CallFragment.ARG_JOIN_ROOM_AUDIO_ONLY, roomMode == RoomMode.AUDIO)
            }
        }

        startActivityWithAnimation(intent)
    }

    open fun onNewPendingInvitation(pendingInvitations: List<WalkieRoomInvitationEvent>) {
        val frag = supportFragmentManager.fragments.firstOrNull { it is WalkieRoomFragment } as? WalkieRoomFragment
        if (frag == null) {
            startActivityWithAnimation(
                    Intent(this, WalkieRoomActivity::class.java)
                            .putParcelableArrayListExtra(WalkieRoomFragment.ARG_PENDING_INVITATIONS, ArrayList(pendingInvitations))
            )

            return
        }

        frag.onNewPendingInvitation(pendingInvitations)
    }


    fun joinRoom(groupIds: List<String> = emptyList(),
                 userIds: List<String> = emptyList(),
                 roomMode: RoomMode = RoomMode.NORMAL) {
        val component = application as AppComponent
        val signalService = component.signalBroker

        showProgressDialog(R.string.getting_room_info, TAG_CREATE_ROOM_PROGRESS)

        signalService.createRoom(userIds = userIds, groupIds = groupIds)
                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : SingleObserver<Room> {
                    override fun onSuccess(t: Room) {
                        hideProgressDialog(TAG_CREATE_ROOM_PROGRESS)
                        joinRoom(roomId = t.id, fromInvitation = false, roomMode = roomMode)
                    }

                    override fun onSubscribe(d: Disposable) {
                        d.bindToLifecycle()
                    }

                    override fun onError(e: Throwable) {
                        logger.e(e) { "Error creating room" }
                        hideProgressDialog(TAG_CREATE_ROOM_PROGRESS)
                        e.toast()
                    }
                })
    }

    open fun onCloseWalkieRoom() {
        (supportFragmentManager.fragments.firstOrNull { it is WalkieRoomFragment } as? WalkieRoomFragment)?.closeRoomPage()
    }

    fun navigateToDialPhone(phoneNumber: String) {
        startActivity(Intent(Intent.ACTION_DIAL).setData(Uri.parse("tel:$phoneNumber")))
    }

    fun navigateToNearByPage() {
        startActivityWithAnimation(
                FragmentDisplayActivity.createIntent(
                        MapFragment::class.java
                )
        )
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
                title = R.string.update_title.toFormattedString(this@BaseActivity, appParams.latestVersionName)
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

        appComponent.appApi.retrieveAppConfig(appComponent.signalBroker.peekUserId() ?: "", appComponent.currentVersion)
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

    private data class JoinRoomBundle(val roomId: String,
                                      val fromInvitation: Boolean,
                                      val roomMode: RoomMode) : Serializable {
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
        const val EXTRA_JOIN_ROOM_MODE = "extra_jrm"
        const val EXTRA_PENDING_INVITATION = "pending_invitation"
        const val EXTRA_NAVIGATE_TO_WALKIE = "navigate_to_walkie"

        private const val STATE_PENDING_DENIED_PERMISSIONS = "state_pending_denied_permissions"

        private val ALL_PERMISSIONS = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
        )
    }


}
