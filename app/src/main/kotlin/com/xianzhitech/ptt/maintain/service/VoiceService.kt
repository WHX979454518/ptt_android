package com.xianzhitech.ptt.maintain.service

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import rx.Completable

data class VoiceServiceJoinRoomRequest(@SerializedName("room") val roomId : String,
                                       @SerializedName("user") val userId : String,
                                       @SerializedName("ssrc") val ssrc : Long,
                                       @SerializedName("cmd") val cmd : String = "join_app")

interface VoiceService {
    @POST("/")
    fun command(@Body request: VoiceServiceJoinRoomRequest) : Completable
}