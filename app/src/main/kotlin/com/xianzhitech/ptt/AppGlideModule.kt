package com.xianzhitech.ptt

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.GlideModule
import java.io.InputStream

class AppGlideModule : GlideModule {
    override fun applyOptions(p0: Context?, p1: GlideBuilder?) {
    }

    override fun registerComponents(context: Context, glide: Glide) {
        glide.register(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory((context.applicationContext as AppComponent).httpClient))
    }
}