package com.xianzhitech.ptt.ui.base

import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView


abstract class BaseToolbarActivity : BaseActivity() {
    private lateinit var rootView : ViewGroup
    protected lateinit var toolbar : Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        super.setContentView(R.layout.activity_base_toolbar)

        rootView = findView(R.id.baseToolbar_root)
        toolbar = findView(R.id.toolbar)
        setSupportActionBar(toolbar)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun setContentView(layoutResID: Int) {
        LayoutInflater.from(this).inflate(layoutResID, rootView, true)
    }

    override fun setContentView(view: View?) {
        rootView.addView(view)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        rootView.addView(view, params)
    }
}