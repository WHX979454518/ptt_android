package com.xianzhitech.ptt.ui

import android.content.Intent
import android.os.Bundle
import com.xianzhitech.ptt.ui.base.BaseActivity

class KickOutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_KICKED_OUT, true)
                .addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT))

        finish()
    }
}