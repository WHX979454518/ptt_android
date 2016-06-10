package com.xianzhitech.ptt.service

import android.os.Build
import android.provider.Settings
import com.google.gson.annotations.SerializedName
import com.xianzhitech.ptt.BuildConfig
import retrofit2.http.Body
import retrofit2.http.POST
import rx.Single

data class AppParams(@SerializedName("update_message") val updateMessage: String?,
                     @SerializedName("force_update") val forceUpdate: Boolean?,
                     @SerializedName("update_url") val updateUrl: String?,
                     @SerializedName("signal_server_endpoint") val signalServerEndpoint: String)

data class AppRequest(@SerializedName("app_version") val appVersion: Int,
                      @SerializedName("app_version_name") val versionName: String,
                      @SerializedName("device_name") val deviceName: String,
                      @SerializedName("model_name") val modelName: String,
                      @SerializedName("device_id") val deviceId: String) {
    companion object {
        val INSTANCE = AppRequest(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, Build.DEVICE, Build.MODEL, Settings.Secure.ANDROID_ID)
    }
}


interface AppService {
    @POST("/params")
    fun retrieveAppParams(@Body request: AppRequest): Single<AppParams>
}