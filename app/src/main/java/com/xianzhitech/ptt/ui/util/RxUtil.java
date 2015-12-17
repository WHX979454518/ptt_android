package com.xianzhitech.ptt.ui.util;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import rx.Observable;
import rx.subscriptions.Subscriptions;

public class RxUtil {

    public static Observable<String> fromTextChanged(final EditText editText) {
        return Observable.create(subscriber -> {
            final TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
                }

                @Override
                public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
                }

                @Override
                public void afterTextChanged(final Editable s) {
                    subscriber.onNext(s.toString());
                }
            };
            editText.addTextChangedListener(watcher);
            subscriber.add(Subscriptions.create(() -> editText.removeTextChangedListener(watcher)));
        });
    }

    public static Observable<Intent> fromBroadcast(final Context context, final String...actions) {
        return Observable.create(subscriber -> {
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    subscriber.onNext(intent);
                }
            };
            final IntentFilter filter = new IntentFilter();
            for (String action : actions) {
                filter.addAction(action);
            }
            context.registerReceiver(receiver, filter);

            subscriber.add(Subscriptions.create(() -> context.unregisterReceiver(receiver)));
        });
    }

    public static <T> Observable<T> fromService(final Context context, final Intent intent, final int flags) {
        return Observable.create(subscriber -> {
            final ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(final ComponentName name, final IBinder service) {
                    subscriber.onNext((T) service);
                }

                @Override
                public void onServiceDisconnected(final ComponentName name) {}
            };
            context.bindService(intent, conn, flags);

            subscriber.add(Subscriptions.create(() -> context.unbindService(conn)));
        });
    }
}
