package com.xianzhitech.ptt.ui

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.view.View
import com.trello.rxlifecycle.ActivityEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.dismissImmediately
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.startActivityForResultWithAnimation
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.home.HomeFragment
import com.xianzhitech.ptt.ui.home.login.LoginFragment
import com.xianzhitech.ptt.ui.user.ContactUserProvider
import com.xianzhitech.ptt.ui.user.UserListActivity
import com.xianzhitech.ptt.update.installPackage

class MainActivity : BaseToolbarActivity(),
        LoginFragment.Callbacks,
        HomeFragment.Callbacks,
        AlertDialogFragment.OnNegativeButtonClickListener,
        AlertDialogFragment.OnPositiveButtonClickListener,
        AlertDialogFragment.OnNeutralButtonClickListener {

    private var pendingCreateRoomRequest: CreateRoomRequest? = null

    companion object {
        val EXTRA_KICKED_OUT = "extra_kicked_out"
        private const val TAG_UPDATE_DIALOG = "tag_update_dialog"
        private const val TAG_PERMISSION_DIALOG = "tag_permission"
        private const val TAG_LOGIN_IN_PROGRESS = "tag_logging_in"

        const val REQUEST_CODE_CREATE_ROOM = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        toolbar.navigationIcon = null

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE), 0)
        }

        if (savedInstanceState == null) {
            handleIntent(intent)

            (application as AppComponent).updateManager.retrieveUpdateInfo()
                    .observeOnMainThread()
                    .compose(bindUntil(ActivityEvent.STOP))
                    .subscribeSimple {
                        if (it != null) {
                            AlertDialogFragment.Builder().apply {
                                title = R.string.update_title.toFormattedString(this@MainActivity)
                                message = it.updateMessage
                                btnPositive = R.string.update.toFormattedString(this@MainActivity)
                                if (it.forceUpdate) {
                                    cancellabe = false
                                }
                                else {
                                    cancellabe = true
                                    btnNeutral = R.string.dialog_ok.toFormattedString(this@MainActivity)
                                }
                                autoDismiss = false
                                attachment = it.updateUrl.toString()
                            }.show(supportFragmentManager, TAG_UPDATE_DIALOG)

                            supportFragmentManager.executePendingTransactions()
                        }
                        else {
                            (supportFragmentManager.findFragmentByTag(TAG_UPDATE_DIALOG) as? DialogFragment)?.let {
                                it.dismiss()
                                supportFragmentManager.executePendingTransactions()
                            }
                        }
                    }

        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        if (pendingCreateRoomRequest != null) {
            joinRoom(pendingCreateRoomRequest!!)
            pendingCreateRoomRequest = null
        }
    }

    override fun requestCreateNewRoom() {
        startActivityForResultWithAnimation(
                UserListActivity.build(this, R.string.create_room.toFormattedString(this), ContactUserProvider(), true, null, emptyList(), false),
                REQUEST_CODE_CREATE_ROOM
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CREATE_ROOM && resultCode == RESULT_OK && data != null) {
            pendingCreateRoomRequest = CreateRoomRequest(extraMemberIds = data.getStringArrayExtra(UserListActivity.RESULT_EXTRA_SELECTED_USER_IDS).toList())
        }
        else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_UPDATE_DIALOG -> fragment.dismissImmediately()
        }
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_UPDATE_DIALOG -> {
                val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val downloadUri = Uri.parse(fragment.attachment as String)
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
                        setTitle(R.string.app_updating.toFormattedString(this@MainActivity, R.string.app_name.toFormattedString(this@MainActivity)))
                    }
                    preference.updateDownloadId = Pair(downloadUri, downloadManager.enqueue(downloadRequest))
                } else if (lastUpdateDownloadId != null) {
                    installPackage(this, lastUpdateDownloadId.second)
                }
            }

            TAG_PERMISSION_DIALOG -> ActivityCompat.requestPermissions(this, arrayOf(fragment.attachment as String), 0)

            else -> super.onPositiveButtonClicked(fragment)
        }
    }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_PERMISSION_DIALOG -> finish()
            else -> super.onNegativeButtonClicked(fragment)
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_KICKED_OUT, false)) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.error_title)
                    .setMessage(R.string.error_forced_logout)
                    .setPositiveButton(R.string.dialog_ok, { dialog, id ->
                        dialog.dismiss()
                    })
                    .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissions.forEachIndexed { i, permission ->
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                onPermissionDenied(permission)
            }
        }

        if (permissions.firstOrNull() == Manifest.permission.READ_PHONE_STATE &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            PhoneCallHandler.register(this)
        }
    }



    private fun onPermissionDenied(permission: String) {
        AlertDialogFragment.Builder().apply {
            title = R.string.error_title.toFormattedString(this@MainActivity)
            message = when (permission) {
                Manifest.permission.RECORD_AUDIO -> R.string.error_no_record_permission
                else -> R.string.error_no_phone_permission
            }.toFormattedString(this@MainActivity)
            btnPositive = R.string.dialog_confirm.toFormattedString(this@MainActivity)
            btnNegative = R.string.dialog_cancel.toFormattedString(this@MainActivity)
            attachment = permission
        }.show(supportFragmentManager, TAG_PERMISSION_DIALOG)
    }

    override fun onBackPressed() {
        supportFragmentManager.findFragmentById(R.id.main_content) ?. let {
            if (it is BackPressable) {
                it.onBackPressed()
                return
            }
        }
        super.onBackPressed()
    }

    override fun onStart() {
        super.onStart()

        val signalService = (application as AppComponent).signalService

        signalService.loginState.distinctUntilChanged { it.status }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    if (it.status == LoginStatus.LOGIN_IN_PROGRESS && it.currentUserID == null) {
                        showProgressDialog(R.string.please_wait, R.string.login_in_progress, TAG_LOGIN_IN_PROGRESS)
                    }
                    else {
                        (supportFragmentManager.findFragmentByTag(TAG_LOGIN_IN_PROGRESS) as? DialogFragment)?.dismissImmediately()
                    }
                }

        signalService.loginState.distinctUntilChanged { it.currentUserID }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe {
                    if (it.currentUserID == null) {
                        displayFragment(LoginFragment::class.java)
                } else {
                        displayFragment(HomeFragment::class.java)
                    }
                }
    }

    private fun displayFragment(fragmentClazz: Class<out Fragment>) {
        if (fragmentClazz != supportFragmentManager.findFragmentById(R.id.main_content)?.javaClass) {
            supportFragmentManager.beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.main_content, Fragment.instantiate(this, fragmentClazz.name))
                    .commit();
        }
    }

}
