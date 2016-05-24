package com.xianzhitech.ptt.ui

import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity


class ServerInitActivity : BaseToolbarActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_server_init)
    }

    private fun start(endpoint : String) {

    }
}