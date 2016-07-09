package com.xianzhitech.ptt.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.gson.annotations.SerializedName
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.Constants
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import rx.Completable
import rx.Single
import java.io.Serializable

class AppConfig(@SerializedName("latest_version_code") val latestVersionCode: Long,
                @SerializedName("latest_version_name") val latestVersionName : String,
                @SerializedName("update_message") val updateMessage: String?,
                @SerializedName("mandatory") val mandatory : Boolean,
                @SerializedName("signal_server_endpoint") val signalServerEndpoint: String,
                @SerializedName("download_url") val downloadUrl : String?) : Serializable {
    val hasUpdate : Boolean
    get() {
        try {
            return BuildConfig.BUILD_NUMBER.toLong() < latestVersionCode
        } catch(e: NumberFormatException) {
            return false
        }
    }

    fun getAppFullName(context: Context) : String {
        return Constants.getAppFullName(context, latestVersionName, latestVersionCode.toString())
    }

    fun getAppFullVersionName() : String {
        return Constants.getAppFullVersionName(latestVersionName, latestVersionCode.toString())
    }
}

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
    @GET("/app_config/{userId}/{version}")
    fun retrieveAppConfig(@Path("userId") userId : String,
                          @Path("version") appVersion : String = BuildConfig.BUILD_NUMBER): Single<AppConfig>

    /**
     * Register a device and get a device id
     */
    @PUT("/device")
    fun registerDevice(@Body appInfo: AppInfo) : Single<String>

    @PUT("/feedback")
    fun submitFeedback(@Body feedback: Feedback) : Completable
}