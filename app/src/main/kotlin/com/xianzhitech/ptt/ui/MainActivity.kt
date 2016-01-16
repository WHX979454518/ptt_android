package com.xianzhitech.ptt.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.service.sio.SocketIOBackgroundService
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeFragment
import com.xianzhitech.ptt.ui.home.login.LoginFragment
import kotlin.collections.forEachIndexed
import kotlin.text.isNullOrEmpty

class MainActivity : BaseActivity(), LoginFragment.Callbacks, HomeFragment.Callbacks {

    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startService(Intent(this, SocketIOBackgroundService::class.java))

        setContentView(R.layout.activity_main)
        toolbar = findView(R.id.main_toolbar)
        setSupportActionBar(toolbar)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        permissions?.forEachIndexed { i, s ->
            if (s == Manifest.permission.RECORD_AUDIO && grantResults!![i] == PackageManager.PERMISSION_DENIED) {
                onPermissionDenied()
            }
        }
    }

    private fun onPermissionDenied() {
        AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(R.string.error_no_android_permission)
                .setPositiveButton(R.string.dialog_confirm, { first, second ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
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

        (application as AppComponent).preferenceProvider.receiveUserToken()
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe {
                    if (it.isNullOrEmpty()) {
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
