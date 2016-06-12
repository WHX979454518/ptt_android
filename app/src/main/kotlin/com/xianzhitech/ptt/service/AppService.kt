package com.xianzhitech.ptt.service

import android.os.Build
import android.provider.Settings
import com.google.gson.annotations.SerializedName
import com.xianzhitech.ptt.BuildConfig
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query
import rx.Completable
import rx.Single
import java.io.Serializable

data class AppParams(@SerializedName("update_message") val updateMessage: String?,
                     @SerializedName("force_update") val forceUpdate: Boolean?,
                     @SerializedName("update_url") val updateUrl: String?,
                     @SerializedName("signal_server_endpoint") val signalServerEndpoint: String) : Serializable

data class Feedback(@SerializedName("title") val title : String,
                    @SerializedName("message") val message : String,
                    @SerializedName("user_id") val userId : String?)


interface AppService {
    @GET("/app_params")
    fun retrieveAppParams(@Query("version") appVersion: Int = BuildConfig.VERSION_CODE,
                          @Query("version_name") versionName : String = BuildConfig.VERSION_NAME,
                          @Query("device") deviceName : String = Build.DEVICE,
                          @Query("model") modelName : String = Build.MODEL,
                          @Query("id") deviceId : String = Settings.Secure.ANDROID_ID): Single<AppParams>

    @PUT("/feedback")
    fun submitFeedback(@Body feedback: Feedback) : Completable
}