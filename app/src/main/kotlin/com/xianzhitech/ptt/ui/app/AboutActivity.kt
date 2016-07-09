package com.xianzhitech.ptt.ui.app

import android.os.Bundle
import android.widget.TextView
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity


class AboutActivity : BaseToolbarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_about)

        findView<TextView>(R.id.about_version).text = Constants.getAppFullName(this, BuildConfig.VERSION_NAME, BuildConfig.BUILD_NUMBER)
    }
}