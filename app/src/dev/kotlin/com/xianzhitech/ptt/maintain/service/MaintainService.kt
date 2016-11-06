package com.xianzhitech.ptt.maintain.service

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class LoginRequest(val username : String, val password : String)
data class EnterpriseQuota(val staff: Int, val preGroup : Int)
data class Enterprise(val name: String,
                      val password: String,
                      val quota: EnterpriseQuota)
data class UserPrivileges(val priority : Int,
                          val callAble : Boolean = true,
                          val groupAble : Boolean = true,
                          val calledAble : Boolean = true,
                          val joinAble : Boolean = true,
                          val forbidSpeak : Boolean = false,
                          val callOuterAble : Boolean = true,
                          val calledOuterAble : Boolean = true)

data class User(val name: String,
                val phoneNbr: String,
                val password: String,
                val privileges: UserPrivileges)

data class Group(val name: String,
                 val description: String,
                 val members : List<String>)

data class ImportRequest(val enterprise: Enterprise,
                         val users : List<User>,
                         val groups : List<Group>)

data class ImportResponse(val enterpriseId : String)

interface MaintainService {
    @GET("/api/init")
    fun init() : Call<Void>

    @POST("/admin/login")
    fun login(@Body request : LoginRequest) : Call<Void>

    @POST("/api/import")
    fun import(@Body importRequest: ImportRequest) : Call<ImportResponse>
}