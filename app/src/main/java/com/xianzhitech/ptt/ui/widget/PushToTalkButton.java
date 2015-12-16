package com.xianzhitech.ptt.ui.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageButton;

/**
 *
 * 带有阴影和效果的对讲按钮
 *
 * Created by fanchao on 12/12/15.
 */
public class PushToTalkButton extends ImageButton {

    public PushToTalkButton(final Context context) {
        super(context);
        init(context);
    }

    public PushToTalkButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PushToTalkButton(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PushToTalkButton(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(final Context context) {
    }
}
