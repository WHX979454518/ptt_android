package com.xianzhitech.ptt.util

import com.xianzhitech.ptt.App
import io.requery.Converter


class SetConverter<T> : Converter<Set<T>, String> {
    override fun getMappedType(): Class<Set<T>> {
        @Suppress("UNCHECKED_CAST")
        return Set::class.java as Class<Set<T>>
    }

    override fun convertToMapped(type: Class<out Set<T>>?, value: String?): Set<T> {
        return App.instance.objectMapper.readValue(value, type) ?: emptySet()
    }

    override fun getPersistedType(): Class<String> {
        return String::class.java
    }

    override fun convertToPersisted(value: Set<T>?): String {
        return App.instance.objectMapper.writeValueAsString(value)
    }

    override fun getPersistedSize(): Int? {
        return -1
    }
}