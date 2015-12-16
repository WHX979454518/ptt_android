package com.xianzhitech.ptt.ui.util;

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
}
