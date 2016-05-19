package com.xianzhitech.ptt.ext

import org.json.JSONArray
import org.json.JSONObject


/**
 * 将该JSON数组转换为字符串数据. 注意转换不复制任何内容.
 */
fun JSONArray?.toStringIterable(): Iterable<String> = transform { it.toString() }

/**
 * 将该JSON数组按需转换. 注意转换不复制和保留任何内容.
 */
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

fun <T> Iterable<T>.toJSONArray() : JSONArray {
    val arr = JSONArray()
    forEach {
        arr.put(it)
    }
    return arr
}

fun JSONObject.nullOrString(name : String) : String? {
    if (isNull(name)) {
        return null
    }

    return optString(name, null)
}

fun JSONObject.getStringValue(name : String, fallback : String = "") : String {
    return nullOrString(name) ?: fallback
}