package com.xianzhitech.ptt.api

import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.api.dto.AppConfig
import com.xianzhitech.ptt.api.dto.Feedback
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.RequestBody
import retrofit2.http.*


interface AppApi {
    @GET("/app_config/{userId}/{version}")
    fun retrieveAppConfig(@Path("userId") userId: String,
                          @Path("version") appVersion: String = BuildConfig.BUILD_NUMBER): Single<AppConfig>

    @PUT("/feedback")
    fun submitFeedback(@Body feedback: Feedback): Completable

    @POST("/logs")
    @Multipart
    fun submitLogs(@PartMap data: MutableMap<String, RequestBody>): Completable
}