package com.xianzhitech.ptt.ext

import android.util.Base64
import android.util.Base64InputStream
import android.util.Base64OutputStream
import java.io.*
import kotlin.collections.toString
import kotlin.text.substring
import kotlin.text.toByteArray

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
fun String.decodeBase64() = Base64.decode(toByteArray(), Base64.NO_WRAP).toString(Charsets.UTF_8)

fun Serializable.serializeToBase64(): String {
    ByteArrayOutputStream().use { rawStream ->
        Base64OutputStream(rawStream, Base64.NO_WRAP).use { encodingStream ->
            ObjectOutputStream(encodingStream).use { objectStream ->
                objectStream.writeObject(this)
            }
        }

        return rawStream.toByteArray().toString(Charsets.UTF_8)
    }
}

fun String?.fromBase64ToSerializable(): Serializable? {
    return this?.let {
        StringBufferInputStream(it).use { rawStream ->
            Base64InputStream(rawStream, Base64.NO_WRAP).use { decodedStream ->
                ObjectInputStream(decodedStream).use {
                    return it.readObject() as Serializable?
                }
            }
        }
    }
}