package com.xianzhitech.ptt.ui.call

import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.databinding.FragmentCallBinding
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.viewmodel.CallViewModel


class CallFragment : BaseViewModelFragment<CallViewModel, FragmentCallBinding>() {
    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentCallBinding {
        return FragmentCallBinding.inflate(inflater, container, false)
    }

    override fun onCreateViewModel(): CallViewModel {
        return CallViewModel()
    }
}