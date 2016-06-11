package com.xianzhitech.ptt.util

import android.support.v4.util.LruCache
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import java.text.Collator
import java.util.*



object PinyinComparator : Comparator<String> {
    private val collator = Collator.getInstance(Locale.CHINESE)

    override fun compare(lhs: String, rhs: String): Int {
        val cmpLen = Math.min(lhs.length, rhs.length)
        for (i in 0..cmpLen-1) {
            val lhsPinyin = lhs[i].toPinyin()
            val rhsPinyin = rhs[i].toPinyin()

            if (lhsPinyin == null && rhsPinyin == null) {
                return collator.compare(lhs, rhs)
            }
            else if (lhsPinyin == null) {
                return -1
            }
            else if (rhsPinyin == null) {
                return 1
            }

            val rc = collator.compare(lhsPinyin, rhsPinyin)
            if (rc != 0) {
                return rc
            }
        }

        return collator.compare(lhs, rhs)
    }
}

inline fun <T> Iterable<T>.sortedByPinyin(crossinline transform : (T) -> String) : List<T> {
    return sortedWith(Comparator<T> { lhs, rhs -> PinyinComparator.compare(transform(lhs), transform(rhs)) })
}

inline fun <T> MutableList<T>.sortByPinyin(crossinline transform : (T) -> String) : List<T> {
    sortWith(Comparator<T> { lhs, rhs -> PinyinComparator.compare(transform(lhs), transform(rhs)) })
    return this
}

fun Char.toPinyin() : String? {
    val c = this.toInt()
    if ((this >= 'A' && this <= 'Z') ||
            this >= 'a' && this <= 'z') {
        // Ascii
        return toString()
    }

    var result : String? = pinyinCache[this]
    if (result == null) {
        result = PinyinHelper.toHanyuPinyinStringArray(this, pinyinFormat)?.firstOrNull()
        if (result != null) {
            pinyinCache.put(this, result)
        }
    }

    if (result == "none0") {
        return null
    }

    return result
}

fun String.toPinyin(out : MutableList<String> = arrayListOf()) : List<String> {
    forEach { c ->
        c.toPinyin()?.let {
            out.add(it)
        }
    }

    return out
}

fun clearPinyinCache() {
    pinyinCache.evictAll()
}

private val pinyinFormat = HanyuPinyinOutputFormat().apply {
    toneType = HanyuPinyinToneType.WITHOUT_TONE
    caseType = HanyuPinyinCaseType.LOWERCASE
}

private val pinyinCache = LruCache<Char, String>(10240)