package com.xianzhitech.ptt.ui.base

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel

abstract class BaseViewModelFragment<T : LifecycleViewModel, VB : ViewDataBinding> : BaseFragment() {
    lateinit var viewModel : T
    private set

    lateinit var dataBinding : VB
    private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = onCreateViewModel()
    }

    override fun onStart() {
        super.onStart()

        viewModel.onStart()
    }

    override fun onStop() {
        viewModel.onStop()

        super.onStop()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dataBinding = onCreateDataBinding(inflater, container)
        dataBinding.setVariable(BR.viewModel, viewModel)
        return dataBinding.root
    }

    override fun onDestroyView() {
        onDestroyViewBinding()
        super.onDestroyView()
    }

    open fun onDestroyViewBinding() {
        dataBinding.unbind()
    }

    abstract fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): VB
    abstract fun onCreateViewModel(): T
}