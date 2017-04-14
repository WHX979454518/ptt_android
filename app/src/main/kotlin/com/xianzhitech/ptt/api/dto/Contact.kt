package com.xianzhitech.ptt.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.xianzhitech.ptt.data.Group
import com.xianzhitech.ptt.data.User


data class Contact(@JsonProperty("enterpriseMembers") val members: List<User>,
                   @JsonProperty("enterpriseGroups") val groups: List<Group>,
                   @JsonProperty("version") val version : Long)