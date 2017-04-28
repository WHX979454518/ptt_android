package com.xianzhitech.ptt.ui.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ui.base.BaseActivity


open class BaseAboutActivity : BaseActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_about)

        findView<TextView>(R.id.about_version).text = getString(R.string.app_name) + "v" + appComponent.currentVersion

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}