package com.xianzhitech.ptt.ui.base

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.ui.chat.ChatFragment
import com.xianzhitech.ptt.ui.room.RoomMemberListFragment
import com.xianzhitech.ptt.ui.user.UserDetailsFragment
import com.xianzhitech.ptt.ui.walkie.WalkieRoomFragment


class FragmentDisplayActivity : BaseActivity(),
        ChatFragment.Callbacks,
        WalkieRoomFragment.Callbacks,
        RoomMemberListFragment.Callbacks,
        UserDetailsFragment.Callbacks {

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.hasExtra(EXTRA_THEME)) {
            setTheme(intent.getIntExtra(EXTRA_THEME, 0))
        }

        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (intent.hasExtra(EXTRA_WINDOW_FLAGS)) {
            window.addFlags(intent.getIntExtra(EXTRA_WINDOW_FLAGS, 0))
        }

        if (savedInstanceState == null) {
            val fragment = Fragment.instantiate(this, intent.getStringExtra(EXTRA_FRAGMENT), intent.getBundleExtra(EXTRA_FRAGMENT_BUNDLE))

            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commitNow()
        }
    }

    override fun onBackPressed() {
        val frag = supportFragmentManager.findFragmentById(android.R.id.content)
        if (frag !is BackPressable || !frag.onBackPressed()) {
            super.onBackPressed()
        }
    }

    companion object {
        private const val EXTRA_FRAGMENT = "extra_fragment"
        private const val EXTRA_FRAGMENT_BUNDLE = "extra_fragment_bundle"

        const val EXTRA_WINDOW_FLAGS = "extra_window_flags"
        const val EXTRA_THEME = "extra_theme"

        fun createIntent(fragmentClass: Class<out Fragment>, args: Bundle? = null): Intent {
            return Intent(BaseApp.instance, FragmentDisplayActivity::class.java)
                    .putExtra(EXTRA_FRAGMENT, fragmentClass.name)
                    .putExtra(EXTRA_FRAGMENT_BUNDLE, args)
        }

        fun createIntent(fragmentClass: Class<out Fragment>, firstKey: String, firstValue: String): Intent {
            return Intent(BaseApp.instance, FragmentDisplayActivity::class.java)
                    .putExtra(EXTRA_FRAGMENT, fragmentClass.name)
                    .putExtra(EXTRA_FRAGMENT_BUNDLE, Bundle(1).apply { putString(firstKey, firstValue) })
        }
    }
}