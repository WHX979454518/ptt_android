package com.xianzhitech.ptt.ui.base

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import com.xianzhitech.ptt.BR
import com.xianzhitech.ptt.viewmodel.LifecycleViewModel
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable


abstract class BaseViewModelActivity<VM : LifecycleViewModel, VB : ViewDataBinding> : AppCompatActivity() {
    private var disposable : CompositeDisposable? = null

    protected lateinit var binding: VB
    protected lateinit var viewModel: VM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = onCreateViewBinding(layoutInflater)
        viewModel = onCreateViewModel()

        binding.setVariable(BR.viewModel, viewModel)

        setContentView(binding.root)
    }

    fun Disposable.bindToLifecycle() : Disposable {
        if (disposable == null) {
            disposable = CompositeDisposable()
        }

        disposable!!.add(this)
        return this
    }

    override fun onStart() {
        super.onStart()

        viewModel.onStart()
    }

    override fun onStop() {
        super.onStop()

        viewModel.onStop()

        disposable?.dispose()
        disposable = null
    }

    abstract fun onCreateViewModel(): VM
    abstract fun onCreateViewBinding(layoutInflater: LayoutInflater): VB
}