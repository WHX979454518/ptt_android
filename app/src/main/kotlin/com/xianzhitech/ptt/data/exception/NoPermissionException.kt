package com.xianzhitech.ptt.data.exception

import com.xianzhitech.ptt.data.Permission


data class NoPermissionException(val permission : Permission?)
    : RuntimeException("No permission $permission")