package com.xianzhitech.ptt.ui.map

import android.os.Bundle
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity

class MapActivity : BaseToolbarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.baseToolbar_root, MapFragment())
                    .commit()
        }
    }
}