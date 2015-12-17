package com.xianzhitech.ptt.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;

import com.xianzhitech.ptt.R;
import com.xianzhitech.ptt.service.user.LoginStatus;
import com.xianzhitech.ptt.service.user.UserService;
import com.xianzhitech.ptt.ui.base.BaseActivity;
import com.xianzhitech.ptt.ui.home.HomeFragment;
import com.xianzhitech.ptt.ui.home.LoginFragment;

import butterknife.Bind;
import butterknife.ButterKnife;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends BaseActivity implements LoginFragment.Callbacks {

    @Bind(R.id.main_toolbar)
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);

        UserService.getLoginStatus(this)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.<Integer>bindToLifecycle())
                .subscribe(status -> {
                    final Class<? extends Fragment> fragmentToDisplay;
                    if (status == LoginStatus.LOGGED_ON) {
                        fragmentToDisplay = HomeFragment.class;
                    }
                    else {
                        fragmentToDisplay = LoginFragment.class;
                    }

                    final Fragment currFragment = getSupportFragmentManager().findFragmentById(R.id.main_content);
                    final Class<? extends Fragment> currFragmentClazz = currFragment == null ? null : currFragment.getClass();

                    if (!(fragmentToDisplay.equals(currFragmentClazz))) {
                        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                        if (currFragment != null) {
                            transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
                        }

                        transaction
                                .replace(R.id.main_content, Fragment.instantiate(MainActivity.this, fragmentToDisplay.getName()))
                                .commit();
                    }
                });
    }
}
