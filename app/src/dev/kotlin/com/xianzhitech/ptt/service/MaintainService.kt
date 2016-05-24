package com.xianzhitech.ptt.service

import okhttp3.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class LoginRequest(val username : String, val password : String)
data class EnterpriseQuota(val staff: Int, val preGroup : Int)
data class EnterpriseCreateRequest(val name: String,
                                   val idNumber : String,
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
                              val idNumber: String,
                              val description: String,
                              val members : List<String>)

data class EnterpriseNumberResponse(val number : String)
data class UserNumberResponse(val number: String)
data class GroupNumberResponse(val groupId: String)

interface MatainService {
    @GET("/api/init")
    fun init() : Response

    @POST("/admin/login")
    fun login(@Body request : LoginRequest) : Response

    @GET("/api/enterprise/number")
    fun createEnterpriseNumber() : EnterpriseNumberResponse

    @POST("/api/enterprise")
    fun createEnterprise(@Body request: EnterpriseCreateRequest) : Response

    @GET("/api/contact/usernumber")
    fun createUserNumber() : UserNumberResponse

    @POST("/api/contact/user")
    fun createUser(@Body request: CreateUserRequest) : Response

    @POST("/api/contact/group")
    fun createGroup(@Body request: CreateGroupRequest) : GroupNumberResponse
}