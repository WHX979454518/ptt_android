package com.xianzhitech.ptt.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.Toolbar
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.ui.base.BaseActivity

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }

        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        toolbar.navigationIcon = getTintedDrawable(R.drawable.ic_arrow_back, Color.WHITE)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}