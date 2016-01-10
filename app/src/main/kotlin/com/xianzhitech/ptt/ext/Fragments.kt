package com.xianzhitech.ptt.ext

import android.os.Bundle
import android.support.v4.app.Fragment

/**
 * Created by fanchao on 10/01/16.
 */

fun Fragment.ensureArguments() = if (arguments == null) {
    Bundle().apply {
        arguments = this
    }
} else {
    arguments
}