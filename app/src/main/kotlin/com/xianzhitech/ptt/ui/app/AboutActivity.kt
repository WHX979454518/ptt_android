package com.xianzhitech.ptt.ui.app

import android.os.Bundle
import android.widget.TextView
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity


class AboutActivity : BaseToolbarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_about)

        findView<TextView>(R.id.about_version).text = "${R.string.app_name.toFormattedString(this)} ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.BUILD_NUMBER})"
    }
}