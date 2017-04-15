package com.xianzhitech.ptt.ui.settings

import android.os.Bundle
import com.xianzhitech.ptt.ui.base.BaseActivity

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, SettingsFragment())
                    .commit()
        }
    }
}