package com.xianzhitech.ptt.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.xianzhitech.ptt.R;
import com.xianzhitech.ptt.ext.ServicesKt;
import com.xianzhitech.ptt.service.user.LoginStatus;
import com.xianzhitech.ptt.service.user.UserService;
import com.xianzhitech.ptt.ui.base.BaseFragment;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.android.schedulers.AndroidSchedulers;

/**
 *
 * 登陆界面
 *
 * Created by fanchao on 17/12/15.
 */
public class LoginFragment extends BaseFragment<LoginFragment.Callbacks> {

    @Bind(R.id.login_nameField)
    EditText nameEdit;

    @Bind(R.id.login_passwordField)
    EditText passwordEdit;

    @Bind(R.id.login_loginBtn)
    View loginBtn;

    @Bind(R.id.login_progress)
    View progressBar;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();

        callbacks.setTitle(getText(R.string.login_title));

        UserService.getLoginStatus(getContext())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.<Integer>bindToLifecycle())
                .subscribe(status -> {
                    switch (status) {
                        case LoginStatus.IDLE: {
                            setInputEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            break;
                        }

                        case LoginStatus.LOGGED_ON: {
                            progressBar.setVisibility(View.GONE);
                            break;
                        }

                        case LoginStatus.LOGIN_IN_PROGRESS: {
                            setInputEnabled(false);
                            progressBar.setVisibility(View.VISIBLE);
                        }
                    }
                });

        ServicesKt.receiveBroadcasts(getContext(), UserService.ACTION_USER_LOGON_FAILED)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.<Intent>bindToLifecycle())
                .subscribe(intent -> new AlertDialog.Builder(getContext())
                        .setTitle(R.string.login_failed)
                        .setMessage(((Throwable) intent.getSerializableExtra(UserService.EXTRA_LOGON_FAILED_REASON)).getMessage())
                        .create()
                        .show()
                );
    }

    private void setInputEnabled(final boolean enabled) {
        nameEdit.setEnabled(enabled);
        passwordEdit.setEnabled(enabled);
        loginBtn.setEnabled(enabled);
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_login, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @OnClick(R.id.login_loginBtn)
    void doLogin() {
        getContext().startService(UserService.buildLogin(getContext(), nameEdit.getText().toString(), passwordEdit.getText().toString()));
        progressBar.setVisibility(View.VISIBLE);
        setInputEnabled(false);
    }

    public interface Callbacks {
        void setTitle(CharSequence title);
    }
}
