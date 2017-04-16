package com.xianzhitech.ptt.data


import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.api.event.Event

class CurrentUser : User, Event {
    @get:JsonProperty("idNumber")
    override val id: String = ""

    @get:JsonProperty("name")
    override val name: String = ""

    @get:JsonProperty("avatar")
    override val avatar: String? = null

    @get:JsonProperty("phoneNumber")
    override val phoneNumber: String? = null

    @get:JsonProperty("enterId")
    val enterpriseId: String? = null

    @get:JsonProperty("enterName")
    val enterpriseName: String? = null

    override val priority: Int
        @JsonIgnore
        get() = Integer.parseInt(privileges["priority"].toString())

    fun hasPermission(perm: Permission?): Boolean {
        Preconditions.checkArgument(perm != null)

        when (perm) {
            Permission.CALL_INDIVIDUAL -> return java.lang.Boolean.TRUE == privileges["callAble"]
            Permission.CALL_TEMP_GROUP -> return java.lang.Boolean.TRUE == privileges["groupAble"]
            Permission.RECEIVE_INDIVIDUAL_CALL -> return java.lang.Boolean.TRUE == privileges["calledAble"]
            Permission.RECEIVE_TEMP_GROUP_CALL -> return java.lang.Boolean.TRUE == privileges["joinAble"]
            Permission.SPEAK -> return java.lang.Boolean.FALSE == privileges["forbidSpeak"]
            Permission.MUTE -> return java.lang.Boolean.TRUE == privileges["muteAble"]
            Permission.FORCE_INVITE -> return java.lang.Boolean.TRUE == privileges["powerInviteAble"]
            Permission.VIEW_MAP -> return java.lang.Boolean.TRUE == privileges["viewMap"]
        }

        return false
    }

    @get:JsonProperty("privileges")
    val privileges: Map<String, Any> = emptyMap()


    @get:JsonProperty("locationTime")
    val locationTime: Map<String, Any> = emptyMap()


    @get:JsonProperty("locationEnable")
    val locationEnabled: Boolean = false


    @get:JsonProperty("locationScanInterval")
    val locationScanIntervalSeconds: Int = -1


    @get:JsonProperty("locationReportInterval")
    val locationReportIntervalSeconds: Int = -1

    @get:JsonProperty("locationWeekly")
    val locationWeekly: IntArray? = null
}
