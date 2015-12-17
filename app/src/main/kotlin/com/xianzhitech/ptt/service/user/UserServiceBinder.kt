package com.xianzhitech.ptt.service.user

import com.xianzhitech.ptt.model.Person

/**
 *
 * 与用户鉴权服务的绑定接口
 *
 * Created by fanchao on 17/12/15.
 */
interface UserServiceBinder {
    val logonUser : Person?

    @LoginStatus
    val loginStatus : Int
}