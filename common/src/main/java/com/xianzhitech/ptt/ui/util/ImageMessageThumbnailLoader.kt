package com.xianzhitech.ptt.ui.util

import android.util.Base64
import android.util.Base64InputStream
import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.stream.StreamModelLoader
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.data.MediaMessageBody
import com.xianzhitech.ptt.data.Message
import java.io.ByteArrayInputStream
import java.io.InputStream


object ImageMessageThumbnailLoader : StreamModelLoader<Message> {

    override fun getResourceFetcher(model: Message, width: Int, height: Int): DataFetcher<InputStream> {
        Preconditions.checkArgument(model.body is MediaMessageBody)

        val body = model.body as MediaMessageBody

        return object : DataFetcher<InputStream> {
            override fun loadData(priority: Priority?): InputStream {
                return Base64InputStream(ByteArrayInputStream(body.thumbnail!!.toByteArray()), Base64.NO_WRAP)
            }

            override fun cleanup() {
            }

            override fun cancel() {
            }

            override fun getId(): String {
                return model.localId ?: model.remoteId!!
            }
        }
    }
}