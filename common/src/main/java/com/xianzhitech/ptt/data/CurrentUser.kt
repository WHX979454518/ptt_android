package com.xianzhitech.ptt.data


import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.base.Preconditions
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.api.event.Event
import com.xianzhitech.ptt.util.Range
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalTime
import org.threeten.bp.ZonedDateTime
import java.util.*

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

    @get:JsonProperty("enterexpTime")
    val enterpriseExpireTime: Long? = null

    override val priority: Int
        @JsonIgnore
        get() = Integer.parseInt(privileges["priority"].toString())

    fun hasPermission(perm: Permission): Boolean {
        when (perm) {
            Permission.CALL_INDIVIDUAL -> return java.lang.Boolean.TRUE == privileges["callAble"]
            Permission.CALL_TEMP_GROUP -> return java.lang.Boolean.TRUE == privileges["groupAble"]
            Permission.RECEIVE_INDIVIDUAL_CALL -> return java.lang.Boolean.TRUE == privileges["calledAble"]
            Permission.RECEIVE_TEMP_GROUP_CALL -> return java.lang.Boolean.TRUE == privileges["joinAble"]
            Permission.SPEAK -> return privileges.containsKey("forbidSpeak").not() || java.lang.Boolean.FALSE == privileges["forbidSpeak"]
            Permission.MUTE -> return java.lang.Boolean.TRUE == privileges["muteAble"]
            Permission.FORCE_INVITE -> return java.lang.Boolean.TRUE == privileges["powerInviteAble"]
            Permission.VIEW_MAP -> return java.lang.Boolean.TRUE == privileges["viewMap"]
        }
    }

    @get:JsonProperty("privileges")
    val privileges: Map<String, Any> = emptyMap()


    @get:JsonProperty("locationTime")
    private val locationTime: Map<String, Any> = emptyMap()


    @get:JsonProperty("locationEnable")
    val locationEnabled: Boolean = false


    @get:JsonProperty("locationScanInterval")
    val locationScanIntervalSeconds: Int = -1


    @get:JsonProperty("locationReportInterval")
    val locationReportIntervalSeconds: Int = -1

    @get:JsonProperty("locationWeekly")
    private val locationWeekly: IntArray? = null

    @get:JsonIgnore
    val locationReportWeekDays: SortedSet<DayOfWeek> by lazy {
        locationWeekly ?: return@lazy ALL_WEEK_DAYS

        val dayOfWeeks = TreeSet<DayOfWeek>()
        locationWeekly.forEachIndexed { index, i ->
            if (i != 0) {
                dayOfWeeks.add(DayOfWeek.of(index + 1))
            }
        }

        dayOfWeeks
    }

    @get:JsonIgnore
    val locationReportTimeStart: LocalTime
        get() = locationTime["from"]?.let { LocalTime.parse(it.toString(), Constants.TIME_FORMAT) } ?: LocalTime.MAX

    @get:JsonIgnore
    val locationReportDurationHours: Int
        get() = (locationTime["last"] as? Number)?.toInt() ?: 24

    @get:JsonIgnore
    val ZonedDateTime.locationReportRange: Range<ZonedDateTime>
        get() = this.with(locationReportTimeStart).let { Range(it, it.plusHours(locationReportDurationHours.toLong())) }


    companion object {
        private val ALL_WEEK_DAYS = EnumSet.allOf(DayOfWeek::class.java).toSortedSet()
    }

}
