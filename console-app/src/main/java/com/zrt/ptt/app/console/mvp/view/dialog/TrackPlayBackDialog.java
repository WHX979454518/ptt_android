package com.zrt.ptt.app.console.mvp.view.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.zrt.ptt.app.console.R;

/**
 * Created by surpass on 2017-5-15.
 */

public class TrackPlayBackDialog extends Dialog {
    public TrackPlayBackDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.track_play_back_dialog);
    }
}
