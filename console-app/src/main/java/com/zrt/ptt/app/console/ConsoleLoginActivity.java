package com.zrt.ptt.app.console;


import android.content.Intent;

import com.xianzhitech.ptt.ui.login.ConsoleLoginBaseActivity;
import com.zrt.ptt.app.console.mvp.view.activity.MainActivity;


public class ConsoleLoginActivity extends ConsoleLoginBaseActivity {
    @Override
    public void navigateToHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
