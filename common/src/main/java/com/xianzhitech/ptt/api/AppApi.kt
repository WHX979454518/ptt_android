package com.xianzhitech.ptt.api

import com.xianzhitech.ptt.api.dto.AppConfig
import com.xianzhitech.ptt.api.dto.Feedback
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.PartMap
import retrofit2.http.Path


interface AppApi {
    @GET("/app_config/{userId}/{version}")
    fun retrieveAppConfig(@Path("userId") userId: String,
                          @Path("version") appVersion: String): Single<AppConfig>

    @PUT("/feedback")
    fun submitFeedback(@Body feedback: Feedback): Completable

    @POST("/logs")
    @Multipart
    fun submitLogs(@PartMap data: MutableMap<String, RequestBody>): Completable
}