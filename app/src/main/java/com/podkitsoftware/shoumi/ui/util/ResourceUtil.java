package com.podkitsoftware.shoumi.ui.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.graphics.drawable.DrawableCompat;

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

    public static Drawable getTintedDrawable(Context context, @DrawableRes int drawableRes, @ColorInt int tintColor) {
        final Drawable wrappedDrawable = DrawableCompat.wrap(getDrawable(context, drawableRes));
        DrawableCompat.setTint(wrappedDrawable, tintColor);
        return wrappedDrawable;
    }

    public static Drawable getTintedDrawableWithColorRes(Context context, @DrawableRes int drawableRes, @ColorRes int tintColor) {
        return getTintedDrawable(context, drawableRes, getColor(context, tintColor));
    }

    public static int getColor(final Context context, @ColorRes int colorRes) {
        if (Build.VERSION.SDK_INT >= 23) {
            context.getColor(colorRes);
        }

        return context.getResources().getColor(colorRes);
    }
}
