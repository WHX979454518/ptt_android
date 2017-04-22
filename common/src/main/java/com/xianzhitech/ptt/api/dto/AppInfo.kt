package com.xianzhitech.ptt.api.dto

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.BuildConfig

class AppInfo(context: Context,
              @JsonProperty("version") val appVersion: Int = BuildConfig.VERSION_CODE,
              @JsonProperty("version_name") val versionName: String = BuildConfig.VERSION_NAME,
              @JsonProperty("device") val deviceName: String = Build.DEVICE,
              @JsonProperty("model") val modelName: String = Build.MODEL,
              @JsonProperty("id") val deviceId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))