package com.xianzhitech.ptt.util

import io.requery.Converter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable


@Suppress("UNCHECKED_CAST")
class SerializableConverter<T : Serializable> : Converter<T, ByteArray> {
    override fun getPersistedSize(): Int? {
        return null
    }

    override fun convertToMapped(type: Class<out T>?, value: ByteArray?): T? {
        return value?.let {
            ObjectInputStream(ByteArrayInputStream(it)).use {
                it.readObject() as? T
            }
        }
    }

    override fun getMappedType(): Class<T> {
        return Serializable::class.java as Class<T>
    }

    override fun getPersistedType(): Class<ByteArray> {
        return ByteArray::class.java
    }

    override fun convertToPersisted(value: T?): ByteArray? {
        return value?.let { v ->
            ByteArrayOutputStream().let {
                ObjectOutputStream(it).use {
                    it.writeObject(v)
                }

                it.toByteArray()
            }
        }
    }
}