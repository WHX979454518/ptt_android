package com.xianzhitech.ptt.api.dto

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.Constants
import java.io.Serializable

data class AppConfig(@JsonProperty("latest_version_code") val latestVersionCode: Long,
                     @JsonProperty("latest_version_name") val latestVersionName: String,
                     @JsonProperty("update_message") val updateMessage: String?,
                     @JsonProperty("mandatory") val mandatory: Boolean,
                     @JsonProperty("signal_server_endpoint") val signalServerEndpoint: String,
                     @JsonProperty("download_url") val downloadUrl: String?) : Serializable {

    fun hasUpdate(version : String) : Boolean {
        val versionCode : Long = version.toLongOrNull() ?: return false
        return versionCode < latestVersionCode
    }

    fun getAppFullName(context: Context): String {
        return Constants.getAppFullName(context, latestVersionName, latestVersionCode.toString())
    }

    fun getAppFullVersionName(): String {
        return Constants.getAppFullVersionName(latestVersionName, latestVersionCode.toString())
    }
}