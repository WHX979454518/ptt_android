package com.xianzhitech.ext

import org.json.JSONArray

/**
 * Created by fanchao on 17/12/15.
 */

fun JSONArray?.toStringList(): Iterable<String> {
    if (this == null) {
        return emptyList()
    }

    return object : Iterable<String> {
        override fun iterator(): Iterator<String> {
            return object : Iterator<String> {
                val index: Int = -1
                override fun next() = getString(index.inc())
                override fun hasNext() = index < length() - 1
            }
        }
    }
}