package com.xianzhitech.ptt.util

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.xianzhitech.ptt.BaseApp
import com.xianzhitech.ptt.data.MessageBody
import com.xianzhitech.ptt.data.MessageType
import io.requery.Convert
import io.requery.Converter


class MessageBodyConverter : Converter<MessageBody, String> {
    override fun getPersistedType(): Class<String> {
        return String::class.java
    }

    override fun getPersistedSize(): Int? {
        return null
    }

    override fun convertToMapped(type: Class<out MessageBody>?, value: String?): MessageBody? {
        return value?.let { BaseApp.instance.objectMapper.readValue(it, MessageBodyWrapper::class.java) }?.body
    }

    override fun convertToPersisted(value: MessageBody?): String? {
        return value?.let { b ->
            BaseApp.instance.objectMapper.writeValueAsString(MessageBodyWrapper().apply {
                body = b
            })
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getMappedType(): Class<MessageBody> {
        return MessageBody::class.java
    }

    private class MessageBodyWrapper {
        @get:JsonProperty("body")
        @get:JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
        @get:Convert(MessageBodyConverter::class)
        var body: MessageBody? = null
    }
}