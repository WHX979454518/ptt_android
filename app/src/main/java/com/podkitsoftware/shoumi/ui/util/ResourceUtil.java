package com.podkitsoftware.shoumi.ui.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;

/**
 * Created by fanchao on 7/12/15.
 */
public class ResourceUtil {

    public static Drawable getDrawable(Context context, @DrawableRes int drawable) {
        if (Build.VERSION.SDK_INT >= 21) {
            return context.getDrawable(drawable);
        }
        return context.getResources().getDrawable(drawable);
    }

    public static int getColor(final Context context, @ColorRes int colorRes) {
        if (Build.VERSION.SDK_INT >= 23) {
            context.getColor(colorRes);
        }

        return context.getResources().getColor(colorRes);
    }
}
