package com.zrt.ptt.app.console;


import android.content.Intent;

import com.xianzhitech.ptt.ui.login.ConsoleLoginBaseActivity;


public class ConsoleLoginActivity extends ConsoleLoginBaseActivity {
    @Override
    public void navigateToHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
