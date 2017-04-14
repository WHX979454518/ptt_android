package com.xianzhitech.ptt.ext

import com.google.common.base.Optional


@Suppress("NOTHING_TO_INLINE")
inline fun <T> T?.toOptional() : Optional<T> {
    return Optional.fromNullable(this)
}