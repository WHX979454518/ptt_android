package com.xianzhitech.ptt.ext

/**
 * Created by fanchao on 16/01/16.
 */

fun <A, B, C> Pair<A, B>.tripleWith(other: C) = Triple(first, second, other)

fun <T, R> T.pairWith(other: R) = Pair(this, other)
