package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
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
}