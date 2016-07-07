package com.xianzhitech.ptt.ui.app

import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity


class ShareActivity : BaseToolbarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_share)

        findView<>()
    }
}