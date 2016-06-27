package com.xianzhitech.ptt.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.gson.annotations.SerializedName
import com.xianzhitech.ptt.BuildConfig
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import rx.Completable
import rx.Single
import java.io.Serializable

class AppParams(@SerializedName("update_message") val updateMessage: String?,
                @SerializedName("force_update") val forceUpdate: Boolean?,
                @SerializedName("update_url") val updateUrl: String?,
                @SerializedName("signal_server_endpoint") val signalServerEndpoint: String) : Serializable

class AppInfo(context : Context,
              @SerializedName("version") val appVersion: Int = BuildConfig.VERSION_CODE,
              @SerializedName("version_name") val versionName : String = BuildConfig.VERSION_NAME,
              @SerializedName("device") val deviceName : String = Build.DEVICE,
              @SerializedName("model") val modelName : String = Build.MODEL,
              @SerializedName("id") val deviceId : String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))

class Feedback(@SerializedName("title") val title : String,
               @SerializedName("message") val message : String,
               @SerializedName("user_id") val userId : String?)


interface AppService {
    @GET("/app_params/{userId}/{version}")
    fun retrieveAppParams(@Path("userId") userId : String,
                          @Path("version") appVersion : Int = BuildConfig.VERSION_CODE): Single<AppParams>

    /**
     * Register a device and get a device id
     */
    @PUT("/device")
    fun registerDevice(@Body appInfo: AppInfo) : Single<String>

    @PUT("/feedback")
    fun submitFeedback(@Body feedback: Feedback) : Completable
}