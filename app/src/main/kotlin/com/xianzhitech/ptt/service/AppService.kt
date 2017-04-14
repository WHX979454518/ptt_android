package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.api.dto.AppConfig
import com.xianzhitech.ptt.api.dto.AppInfo
import com.xianzhitech.ptt.api.dto.Feedback
import okhttp3.RequestBody
import retrofit2.http.*
import rx.Completable
import rx.Single


interface AppService {
    @GET("/app_config/{userId}/{version}")
    fun retrieveAppConfig(@Path("userId") userId: String,
                          @Path("version") appVersion: String = BuildConfig.BUILD_NUMBER): Single<AppConfig>

    /**
     * Register a device and get a device id
     */
    @PUT("/device")
    fun registerDevice(@Body appInfo: AppInfo): Single<String>

    @PUT("/feedback")
    fun submitFeedback(@Body feedback: Feedback): Completable

    @POST("/logs")
    @Multipart
    fun submitLogs(@PartMap data: MutableMap<String, RequestBody>): Completable
}