package com.zrt.ptt.app.console;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.xianzhitech.ptt.ui.home.login.LoginFragment;
import com.xianzhitech.ptt.ui.login.LoginActivity;
import com.zrt.ptt.app.console.mvp.view.activity.MainActivity;
import com.zrt.ptt.app.console.ui.login.ConsoleLoginFragment;

import org.jetbrains.annotations.NotNull;


public class ConsoleLoginActivity extends LoginActivity {
    @Override
    public void navigateToHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @NotNull
    @Override
    public Fragment onCreateFragment() {
        final ConsoleLoginFragment fragment = new ConsoleLoginFragment();
        final Bundle args = new Bundle(2);
        args.putBoolean(LoginFragment.ARG_KICKED_OUT, getIntent().getBooleanExtra(EXTRA_KICKED_OUT, false));
        args.putString(LoginFragment.ARG_KICKED_OUT_REASON, getIntent().getStringExtra(EXTRA_KICKED_OUT_REASON));
        fragment.setArguments(args);
        return fragment;
    }
}
