package com.xianzhitech.ptt.ext

import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.emptyList

/**
 * Created by fanchao on 17/12/15.
 */

fun JSONArray?.toStringIterable(): Iterable<String> = transform { it.toString() }

fun JSONArray?.toStringList(): List<String> = this?.let {
    ArrayList<String>(length()).apply {
        for (i in 0..length() - 1) {
            add(getString(i))
        }
    }
} ?: emptyList<String>()

fun <T> JSONArray?.transform(map: (Any) -> T): Iterable<T> {
    if (this == null) {
        return emptyList()
    }

    return object : Iterable<T> {
        override fun iterator(): Iterator<T> {
            return object : Iterator<T> {
                var index: Int = -1
                override fun next() : T  {
                    index += 1
                    return map(get(index))
                }

                override fun hasNext() = index < length() - 1
            }
        }
    }
}


fun <K, V> Map<K, V>.toJSONObject() = JSONObject(this)

inline fun <T> Iterable<T>?.toJSONArray(mapper : (T) -> Any) : JSONArray {
    var result = JSONArray()
    if (this == null) {
        return result
    }

    for (obj in this) {
        result.put(mapper(obj))
    }

    return result
}

