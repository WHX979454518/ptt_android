package com.xianzhitech.ptt.ui.chat

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.databinding.FragmentChatBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.show
import com.xianzhitech.ptt.ext.toRxObservable
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class ChatFragment : BaseViewModelFragment<ChatViewModel, FragmentChatBinding>(), ChatViewModel.Navigator {
    private val chatAdapter = ChatAdapter()

    private val inputMethodManager: InputMethodManager by lazy {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentChatBinding {
        val binding = FragmentChatBinding.inflate(inflater, container, false)
        binding.recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true)
        binding.recyclerView.adapter = chatAdapter
        return binding
    }

    override fun onCreateViewModel(): ChatViewModel {
        return ChatViewModel(appComponent, context.applicationContext, chatAdapter.messages, arguments.getString(ARG_ROOM_ID), this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dataBinding.editText.requestFocus()
        dataBinding.editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewModel.moreSelectionOpen.set(false)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        if (activity is FragmentDisplayActivity) {
            viewModel.title.toRxObservable()
                    .subscribe { activity.title = it.orNull() }
                    .bindToLifecycle()

            viewModel.moreSelectionOpen.toRxObservable()
                    .distinctUntilChanged()
                    .subscribe {
                        if (it.get()) {
                            dataBinding.editText.clearFocus()
                            inputMethodManager.hideSoftInputFromWindow(dataBinding.editText.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                        }
                        else {
                            inputMethodManager.showSoftInput(dataBinding.editText, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                    .bindToLifecycle()

            viewModel.moreSelectionOpen.toRxObservable()
                    .distinctUntilChanged()
                    .switchMap { open ->
                        if (open.get()) {
                            Observable.timer(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread()).map { true }
                        } else {
                            Observable.just(open.get())
                        }
                    }
                    .subscribe { dataBinding.bottomSelection.show = it }
                    .bindToLifecycle()
        }
    }

    override fun navigateToVideoChatPage(roomId: String) {
        callbacks<Callbacks>()?.navigateToVideoChatPage(roomId)
    }

    override fun navigateToWalkieTalkie(roomId: String) {
        callbacks<Callbacks>()?.navigateToWalkieTalkiePage(roomId)
    }

    override fun displayNoPermissionToWalkie() {
        Toast.makeText(context, R.string.error_no_permission, Toast.LENGTH_LONG).show()
    }


    override fun openEmojiDrawer() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun navigateToLatestMessageIfPossible() {
        val position = (dataBinding.recyclerView.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition()
        if (position == 1) {
            dataBinding.recyclerView.smoothScrollToPosition(0)
        }
    }

    override fun navigateToWalkieTalkiePage() {
        callbacks<Callbacks>()?.navigateToWalkieTalkiePage()
    }

    override fun navigateToVideoChatPage() {
        callbacks<Callbacks>()?.navigateToVideoChatPage()
    }

    interface Callbacks {
        fun navigateToWalkieTalkiePage(roomId: String)
        fun navigateToWalkieTalkiePage()
        fun navigateToVideoChatPage()
        fun navigateToVideoChatPage(roomId: String)
    }

    companion object {
        const val ARG_ROOM_ID = "room_id"

        fun createInstance(roomId: String): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_ROOM_ID, roomId)
                }
            }
        }
    }
}