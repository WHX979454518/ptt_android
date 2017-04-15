package com.xianzhitech.ptt.ui.base

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import com.xianzhitech.ptt.App


class FragmentDisplayActivity : AppCompatActivity() {

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val fragment = Fragment.instantiate(this, intent.getStringExtra(EXTRA_FRAGMENT), intent.getBundleExtra(EXTRA_FRAGMENT_BUNDLE))

            supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .commitNow()
        }
    }

    companion object {
        private const val EXTRA_FRAGMENT = "extra_fragment"
        private const val EXTRA_FRAGMENT_BUNDLE = "extra_fragment_bundle"

        fun createIntent(fragmentClass: Class<out Fragment>, args : Bundle? = null): Intent {
            return Intent(App.instance, FragmentDisplayActivity::class.java)
                    .putExtra(EXTRA_FRAGMENT, fragmentClass.name)
                    .putExtra(EXTRA_FRAGMENT_BUNDLE, args)
        }
    }
}