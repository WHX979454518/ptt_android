package com.xianzhitech.ptt.ui.base

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable


abstract class BaseViewModelActivity<VM : LifecycleViewModel, VB : ViewDataBinding> : BaseActivity() {
    protected lateinit var binding: VB
    protected lateinit var viewModel: VM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = onCreateViewBinding(layoutInflater)
        viewModel = onCreateViewModel()
        savedInstanceState?.let(viewModel::onRestoreState)

        binding.setVariable(BR.viewModel, viewModel)

        setContentView(binding.root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        viewModel.onSaveState(outState)
    }

    override fun onStart() {
        super.onStart()

        viewModel.onStart()
    }

    override fun onStop() {
        super.onStop()

        viewModel.onStop()
    }

    abstract fun onCreateViewModel(): VM
    abstract fun onCreateViewBinding(layoutInflater: LayoutInflater): VB
}