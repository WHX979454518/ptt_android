package com.xianzhitech.ptt.ui.call

import android.os.Bundle
import com.xianzhitech.ptt.databinding.ActivityCallBinding
import com.xianzhitech.ptt.ui.base.BaseActivity

class CallActivity : BaseActivity() {
    private lateinit var binding : ActivityCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}