package com.xianzhitech.ptt.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.service.user.LoginStatus
import com.xianzhitech.ptt.service.user.UserService
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.home.HomeFragment
import com.xianzhitech.ptt.ui.home.LoginFragment

class MainActivity : BaseActivity(), LoginFragment.Callbacks, HomeFragment.Callbacks {

    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        toolbar = findView(R.id.main_toolbar)
        setSupportActionBar(toolbar)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }

        UserService.getLoginStatus(this)
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe { status ->
                    val fragmentToDisplay: Class<out Fragment>
                    if (status == LoginStatus.LOGGED_ON) {
                        fragmentToDisplay = HomeFragment::class.java
                    } else {
                        fragmentToDisplay = LoginFragment::class.java
                    }

                    val currFragment = supportFragmentManager.findFragmentById(R.id.main_content)
                    val currFragmentClazz = currFragment?.javaClass

                    if (fragmentToDisplay != currFragmentClazz) {
                        val transaction = supportFragmentManager.beginTransaction()
                        if (currFragment != null) {
                            transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        }

                        transaction.replace(R.id.main_content, Fragment.instantiate(this@MainActivity, fragmentToDisplay.name)).commit()
                    }
                }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {

    }
}
