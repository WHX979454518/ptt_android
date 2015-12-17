package com.xianzhitech.ext

import org.json.JSONArray
import org.json.JSONObject

/**
 * Created by fanchao on 17/12/15.
 */

fun JSONArray?.toStringList(): Iterable<String> = transform { it.toString() }

fun <T> JSONArray?.transform(map: (Any) -> T): Iterable<T> {
    if (this == null) {
        return emptyList()
    }

    return object : Iterable<T> {
        override fun iterator(): Iterator<T> {
            return object : Iterator<T> {
                val index: Int = -1
                override fun next() = map(get(index.inc()))
                override fun hasNext() = index < length() - 1
            }
        }
    }
}

fun <K, V> Map<K, V>.toJSONObject() = JSONObject(this)

