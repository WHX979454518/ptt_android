package com.xianzhitech.ptt.service

import com.google.gson.annotations.SerializedName
import io.reactivex.Completable
import retrofit2.http.Body
import retrofit2.http.POST

data class VoiceServiceJoinRoomRequest(@SerializedName("room") val roomId : String,
                                       @SerializedName("user") val userId : String,
                                       @SerializedName("ssrc") val ssrc : Long,
                                       @SerializedName("cmd") val cmd : String = "join_app")

interface VoiceService {
    @POST("/")
    fun command(@Body request: VoiceServiceJoinRoomRequest) : Completable
}