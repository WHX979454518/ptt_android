package com.xianzhitech.ptt.ui.home

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.xianzhitech.ptt.ui.roomlist.RoomListFragment


class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, RoomListFragment())
                    .commit()
        }
    }
}