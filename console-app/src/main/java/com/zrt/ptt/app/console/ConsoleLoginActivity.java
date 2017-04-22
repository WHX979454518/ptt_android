package com.zrt.ptt.app.console;


import android.content.Intent;

import com.xianzhitech.ptt.ui.login.LoginActivity;

public class ConsoleLoginActivity extends LoginActivity {
    @Override
    public void navigateToHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
