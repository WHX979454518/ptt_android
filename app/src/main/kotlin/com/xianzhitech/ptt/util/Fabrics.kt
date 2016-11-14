package com.xianzhitech.ptt.util

import com.crashlytics.android.answers.AnswersEvent
import com.xianzhitech.ptt.model.User


fun <T : AnswersEvent<*>> T.withUser(userId : String?, user : User?) : T {
    putCustomAttribute("userId", userId)
    if (user != null) {
        putCustomAttribute("userName", user.name)
        putCustomAttribute("enterpriseName", user.enterpriseName)
    }

    return this
}