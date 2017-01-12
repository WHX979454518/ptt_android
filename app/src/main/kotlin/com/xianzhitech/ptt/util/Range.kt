package com.xianzhitech.ptt.util

data class Range<T : Comparable<T>>(val start : T,
                                    val end : T) : Comparable<Range<T>> {

    override fun compareTo(other: Range<T>): Int {
        return start.compareTo(other.start)
    }

    fun contains(v : T) : Boolean {
        return v >= start && v <= end
    }
}