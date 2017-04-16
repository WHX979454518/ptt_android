package com.xianzhitech.ptt.data


data class CurrentWalkieRoom(val roomId : String,
                             val room : Room?,
                             val initiatorUserId : String,
                             val currentSpeakerId : String?,
                             val currentSpeakerPriority : Int?,
                             val onlineMemberIds : Set<String>)