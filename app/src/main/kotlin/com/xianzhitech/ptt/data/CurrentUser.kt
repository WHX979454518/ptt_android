package com.xianzhitech.ptt.data

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.Constants
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalTime
import java.util.*


class CurrentUser : User {
    @get:JsonProperty("idNumber")
    override lateinit var id: String
    private set

    @get:JsonProperty("name")
    override lateinit var name: String
    private set

    @get:JsonProperty("avatar")
    override var avatar: String? = null
    private set

    @get:JsonIgnore
    override val priority: Int
    get() = (privileges["priority"] as Number).toInt()

    @get:JsonProperty("phoneNumber")
    override var phoneNumber: String? = null
    private set

    @get:JsonProperty("enterId")
    override lateinit var enterpriseId: String
    private set

    @get:JsonProperty("enterName")
    override lateinit var enterpriseName: String
    private set

    @get:JsonProperty("privileges")
    lateinit var privileges : Map<String, Any>
    private set

    @get:JsonProperty("locationEnable")
    var locationEnabled : Boolean = false
    private set

    @get:JsonProperty("locationScanInterval")
    var locationScanIntervalSeconds : Int = -1
    private set

    @get:JsonProperty("locationReportInterval")
    var locationReportIntervalSeconds : Int = -1
    private set

    @field:JsonProperty("locationWeekly")
    private var locationWeekly : Array<Int> = emptyArray()

    @get:JsonProperty("locationTime")
    private var locationTime : Map<String, Any> = emptyMap()

    @get:JsonIgnore
    val locationReportTimeStart : LocalTime
    get() = locationTime["from"]?.let { LocalTime.parse(it.toString(), Constants.TIME_FORMAT) } ?: LocalTime.MIN

    @get:JsonIgnore
    val locationReportDurationHours : Int
    get() = locationTime["last"]?.let { (it as Number).toInt() } ?: 24

    @get:JsonIgnore
    val locationReportWeekDays : SortedSet<DayOfWeek>
    get() = locationWeekly.mapIndexedNotNull { index, reports ->
        if (reports != 0) DayOfWeek.of(index + 1) else null
    }.toSortedSet()
}