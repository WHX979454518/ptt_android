package com.xianzhitech.ptt;

import android.app.ProgressDialog;
import android.os.Bundle;

import com.xianzhitech.ptt.ext.GlobalSubscriber;
import com.xianzhitech.ptt.model.User;
import com.xianzhitech.ptt.repo.RepositoriesKt;
import com.xianzhitech.ptt.service.BackgroundServiceBinder;
import com.xianzhitech.ptt.ui.base.BaseActivity;

import net.hockeyapp.android.FeedbackManager;
import net.hockeyapp.android.objects.FeedbackUserDataElement;

import org.jetbrains.annotations.NotNull;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;

/**
 * Created by fanchao on 23/01/16.
 */
public class FeedbackActivity extends BaseActivity {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FeedbackManager.register(this, BuildConfig.HOCKEYAPP_ID, null);
        FeedbackManager.setRequireUserEmail(FeedbackUserDataElement.OPTIONAL);
        FeedbackManager.setRequireUserName(FeedbackUserDataElement.OPTIONAL);

        final ProgressDialog show = ProgressDialog.show(this, getString(R.string.please_wait), null, true);

        final AppComponent appComponent = (AppComponent) getApplication();
        appComponent.getBackgroundService()
                .flatMap(new Func1<BackgroundServiceBinder, Observable<User>>() {
                    @Override
                    public Observable<User> call(final BackgroundServiceBinder backgroundServiceBinder) {
                        return RepositoriesKt.optUser(appComponent.getUserRepository(), backgroundServiceBinder.peekLoginState().getCurrentUserID());
                    }
                })
                .first()
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.<User>bindToLifecycle())
                .subscribe(new GlobalSubscriber<User>(this) {
                    @Override
                    public void onNext(final User logonUser) {
                        FeedbackManager.setUserName(logonUser == null ? "" : logonUser.getName());
                        FeedbackManager.showFeedbackActivity(FeedbackActivity.this);
                        finish();

                        show.dismiss();
                    }

                    @Override
                    public void onError(@NotNull final Throwable e) {
                        super.onError(e);
                        show.dismiss();
                    }
                });
    }
}
