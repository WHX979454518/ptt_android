package com.zrt.ptt.app.console.mvp.view.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.GridView;

import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.view.adapter.TraceGridAdapter;

/**
 * Created by surpass on 2017-5-15.
 */

public class TrackPlayBackDialog extends Dialog {
    private TraceGridAdapter gridAdapter ;
    private GridView gridView;
    private Context context;
    public TrackPlayBackDialog(@NonNull Context context) {
        super(context);
        this.context = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.track_play_back_dialog);
        gridView = (GridView) findViewById(R.id.trace_user_label_grid);
        gridAdapter = new TraceGridAdapter(null,context);
        gridView.setAdapter(gridAdapter);

    }
}
