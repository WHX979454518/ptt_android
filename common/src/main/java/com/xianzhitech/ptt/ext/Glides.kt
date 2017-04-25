package com.xianzhitech.ptt.ext

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Base64OutputStream
import com.bumptech.glide.Glide
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import com.xianzhitech.ptt.BaseApp
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import java.io.ByteArrayOutputStream
import java.lang.Exception


fun loadImageAsBase64(uri : String, width: Int, height: Int) : Single<String> {
    return Single.create<String> { emitter ->
        Glide.with(BaseApp.instance)
                .load(uri)
                .asBitmap()
                .override(width, height)
                .atMost()
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, glideAnimation: GlideAnimation<in Bitmap>?) {
                        val byteOut = ByteArrayOutputStream()
                        Base64OutputStream(byteOut, Base64.NO_WRAP).use {
                            if (resource.compress(Bitmap.CompressFormat.JPEG, 100, it).not()) {
                                emitter.onError(RuntimeException("Can not compress bitmap"))
                                return
                            }
                        }

                        emitter.onSuccess(String(byteOut.toByteArray(), Charsets.UTF_8))
                    }

                    override fun onLoadFailed(e: Exception?, errorDrawable: Drawable?) {
                        super.onLoadFailed(e, errorDrawable)
                        emitter.onError(e)
                    }
                })

    }.subscribeOn(AndroidSchedulers.mainThread())
}