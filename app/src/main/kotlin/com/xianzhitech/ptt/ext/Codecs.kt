package com.xianzhitech.ptt.ext

import android.util.Base64

/**
 * 将字串转换为MD5串
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

/**
 * 将本字串以UTF8编码转换为base64字串
 */
fun String.toBase64() : String = Base64.encodeToString(toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

fun String.containsOnlyAsciiChars() : Boolean {
    forEach {
        if (!((it >= 'a' && it <= 'z') ||
                (it >= 'A' && it <= 'Z'))) {
            return false
        }
    }

    return true
}