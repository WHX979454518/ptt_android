package com.xianzhitech.ptt.service

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class LoginRequest(val username : String, val password : String)
data class EnterpriseQuota(val staff: Int, val preGroup : Int)
data class EnterpriseCreateRequest(val name: String,
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

data class CreateUserRequest(val name: String,
                             val idNumber: String,
                             val phoneNbr: String,
                             val password: String,
                             val privileges: UserPrivileges)

data class CreateGroupRequest(val name: String,
                              val description: String,
                              val members : List<String>)

data class CreateEnterpriseResponse(val idNumber: String)
data class UserNumberResponse(val number: String)
data class GroupNumberResponse(val groupId: String)

interface MaintainService {
    @GET("/api/init")
    fun init() : Call<Void>

    @POST("/admin/login")
    fun login(@Body request : LoginRequest) : Call<Void>

    @POST("/api/enterprise")
    fun createEnterprise(@Body request: EnterpriseCreateRequest) : Call<CreateEnterpriseResponse>

    @GET("/api/contact/usernumber")
    fun createUserNumber() : Call<UserNumberResponse>

    @POST("/api/contact/user")
    fun createUser(@Body request: CreateUserRequest) : Call<Void>

    @POST("/api/contact/group")
    fun createGroup(@Body request: CreateGroupRequest) : Call<GroupNumberResponse>
}