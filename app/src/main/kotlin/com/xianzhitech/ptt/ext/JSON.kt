package com.xianzhitech.ptt.ext

import org.json.JSONArray

/**
 * Created by fanchao on 17/12/15.
 */

fun JSONArray?.toStringIterable(): Iterable<String> = transform { it.toString() }

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
