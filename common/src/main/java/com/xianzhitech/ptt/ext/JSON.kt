package com.xianzhitech.ptt.ext

import org.json.JSONArray
import org.json.JSONObject
import java.util.*


/**
 * 将该JSON数组转换为字符串数据. 注意转换不复制任何内容.
 */
fun JSONArray?.toStringList(): List<String> = transform { it.toString() }

/**
 * 将该JSON数组按需转换. 注意转换不复制和保留任何内容.
 */
fun <T> JSONArray?.transform(map: (Any) -> T): List<T> {
    val jsonArray = this ?: return emptyList()

    return object : AbstractList<T>() {
        override val size: Int
            get() = jsonArray.length()

        override fun get(index: Int): T {
            return map(jsonArray.get(index))
        }
    }
}

fun <T> Iterable<T>.toJSONArray(): JSONArray {
    val arr = JSONArray()
    forEach {
        arr.put(it)
    }
    return arr
}

fun JSONObject.nullOrString(name: String): String? {
    if (isNull(name)) {
        return null
    }

    return optString(name, null)
}

fun JSONObject.getStringValue(name: String, fallback: String = ""): String {
    return nullOrString(name) ?: fallback
}