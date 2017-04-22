package com.xianzhitech.ptt.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.xianzhitech.ptt.databinding.FragmentUserDetailsBinding
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ui.base.BaseViewModelFragment


class UserDetailsFragment : BaseViewModelFragment<UserDetailsViewModel, FragmentUserDetailsBinding>() {
    override fun onCreateDataBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentUserDetailsBinding {
        return FragmentUserDetailsBinding.inflate(inflater, container, false)
    }

    override fun onCreateViewModel(): UserDetailsViewModel {
        return UserDetailsViewModel(appComponent, callbacks<Callbacks>()!!, arguments.getString(ARG_USER_ID))
    }

    interface Callbacks : UserDetailsViewModel.Navigator

    companion object {
        const val ARG_USER_ID = "user_id"

        fun createInstance(userId : String) : UserDetailsFragment {
            return UserDetailsFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_USER_ID, userId)
                }
            }
        }
    }

}