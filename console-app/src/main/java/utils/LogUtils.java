package utils;

import android.util.Log;

/**
 * Created by hefei on 2017/5/13.
 */

public class LogUtils {
    public static void d(String tag, String msg){
        Log.d(tag, msg);
    }

    public static void e(String tag, String msg){
        Log.e(tag, msg);
    }
}
