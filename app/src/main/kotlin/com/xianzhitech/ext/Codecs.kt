package com.xianzhitech.ext

import android.util.Base64

/**
 * Created by fanchao on 17/12/15.
 */

fun String.toMD5(): String {
    val md = java.security.MessageDigest.getInstance("MD5")
    val array = md.digest(toByteArray())
    val sb = StringBuilder()
    for (b in array) {
        sb.append(Integer.toHexString((b.toInt() and 255) or 256).substring(1, 3))
    }
    return sb.toString()
}

fun String.toBase64() = Base64.encodeToString(toByteArray(Charsets.UTF_8), Base64.NO_WRAP)