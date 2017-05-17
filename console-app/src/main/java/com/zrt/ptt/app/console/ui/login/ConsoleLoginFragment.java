package com.zrt.ptt.app.console.ui.login;

import android.databinding.ViewDataBinding;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.xianzhitech.ptt.ui.home.login.LoginFragment;
import com.zrt.ptt.app.console.databinding.ConsoleLoginFragmentBinding;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ConsoleLoginFragment extends LoginFragment {
    @NotNull
    @Override
    public ViewDataBinding onCreateDataBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return ConsoleLoginFragmentBinding.inflate(inflater, container, false);
    }
}
