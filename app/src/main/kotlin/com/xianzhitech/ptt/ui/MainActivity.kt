package com.xianzhitech.ptt.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import com.trello.rxlifecycle.ActivityEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.AlertDialogFragment
import com.xianzhitech.ptt.ui.home.HomeFragment
import com.xianzhitech.ptt.ui.home.login.LoginFragment

class MainActivity : BaseActivity(), LoginFragment.Callbacks, HomeFragment.Callbacks {
    companion object {
        val EXTRA_KICKED_OUT = "extra_kicked_out"
    }

    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        toolbar = findView(R.id.main_toolbar)
        setSupportActionBar(toolbar)

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
                            }.show(supportFragmentManager, UPDATE_DIALOG_TAG)

                            supportFragmentManager.executePendingTransactions()
                        }
                        else {
                            (supportFragmentManager.findFragmentByTag(UPDATE_DIALOG_TAG) as? DialogFragment)?.let {
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
        AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(when (permission) {
                    Manifest.permission.RECORD_AUDIO -> R.string.error_no_record_permission
                    else -> R.string.error_no_phone_permission
                })
                .setPositiveButton(R.string.dialog_confirm, { first, second ->
                    ActivityCompat.requestPermissions(this, arrayOf(permission), 0)
                })
                .setNegativeButton(R.string.dialog_cancel, { first, second ->
                    finish()
                })
                .show()
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

        (application as AppComponent).signalService.loginState
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
