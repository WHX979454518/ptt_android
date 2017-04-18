package com.xianzhitech.ptt.util

import io.requery.Converter
import org.json.JSONObject


class JSONObjectConverter : Converter<JSONObject, String> {
    override fun convertToMapped(type: Class<out JSONObject>?, value: String?): JSONObject? {
        return value?.let(::JSONObject)
    }

    override fun getPersistedType(): Class<String> {
        return String::class.java
    }

    override fun getMappedType(): Class<JSONObject> {
        return JSONObject::class.java
    }

    override fun convertToPersisted(value: JSONObject?): String? {
        return value?.toString()
    }

    override fun getPersistedSize(): Int? {
        return null
    }
}