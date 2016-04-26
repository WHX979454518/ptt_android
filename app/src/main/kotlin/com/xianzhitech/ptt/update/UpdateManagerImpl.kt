package com.xianzhitech.ptt.update

import android.content.Context
import android.net.Uri
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.onSingleValue
import org.json.JSONObject
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import java.net.URL
import java.util.*


class UpdateManagerImpl(private val appContext : Context,
                        private val endpoint : Uri) : UpdateManager {
    companion object {
        private const val MIN_UPDATE_CHECK_INTERVAL = 5 * 60 * 1000L // 5min
    }

    private var updateInfoSubject : BehaviorSubject<UpdateInfo>? = null
    private var lastCheckDate : Date? = null

    override fun retrieveUpdateInfo(): Observable<UpdateInfo?> {
        return synchronized(this, {
            if (updateInfoSubject == null || lastCheckDate == null || updateInfoSubject!!.hasThrowable() ||
                    System.currentTimeMillis() - lastCheckDate!!.time > MIN_UPDATE_CHECK_INTERVAL) {
                updateInfoSubject = BehaviorSubject.create()
                lastCheckDate = Date()

                Observable.create<UpdateInfo?> { subscriber ->
                    val uri = endpoint.buildUpon()
                            .appendQueryParameter("version", BuildConfig.BUILD_NUMBER)
                            .appendQueryParameter("package", appContext.packageName)
                            .build()

                    try {
                        URL(uri.toString()).openConnection().inputStream.bufferedReader(Charsets.UTF_8).use {
                            val rawText = it.readText()
                            if (rawText.isNullOrEmpty() || rawText.equals("null", ignoreCase = true)) {
                                subscriber.onSingleValue(null)
                            }
                            else {
                                val obj = JSONObject(rawText)
                                subscriber.onSingleValue(UpdateInfo(
                                        updateMessage = obj.optString("updateMessage"),
                                        updateUrl = Uri.parse(obj.optString("updateUrl")),
                                        forceUpdate = obj.optBoolean("forceUpdate", false)))
                            }
                        }
                    } catch(e: Throwable) {
                        subscriber.onError(e)
                    }

                }.subscribeOn(Schedulers.io())
                .subscribe(object : GlobalSubscriber<UpdateInfo?>() {
                    override fun onNext(t: UpdateInfo?) {
                        updateInfoSubject!!.onNext(t)
                    }

                    override fun onError(e: Throwable) {
                        updateInfoSubject!!.onError(e)
                    }
                })
            }

            updateInfoSubject!!
        })
    }
}