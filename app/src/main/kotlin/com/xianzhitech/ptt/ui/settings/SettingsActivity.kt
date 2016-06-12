package com.xianzhitech.ptt.ui.settings

import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity

class SettingsActivity : BaseToolbarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_container, SettingsFragment())
                    .commit()
        }
    }
}