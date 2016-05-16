package com.xianzhitech.ptt.ext

import java.util.*

/**
 * 变换一个Iterable, 不会生成新的对象, 每次next都会呼叫指定的转换函数
 */
fun <T, R> Iterable<T>.transform(mapper: (T) -> R) = object : Iterable<R> {
    override fun iterator(): Iterator<R> {
        return object : Iterator<R> {
            val origIterator = this@transform.iterator()

            override fun next() = mapper(origIterator.next())
            override fun hasNext() = origIterator.hasNext()
        }
    }

}

fun <T> Iterable<T>.first(count : Int) : List<T> {
    if (this is List) {
        return subList(0, Math.min(count - 1, size - 1))
    }
    else {
        return mapFirst(count, { it })
    }
}

inline fun <T, R> Iterable<T>.mapFirst(count : Int, transform : (T) -> R) : List<R> {
    val result = ArrayList<R>(count)
    var index = 0
    val iter = iterator()
    while (index < count && iter.hasNext()) {
        result.add(transform(iter.next()))
        index++
    }
    return result
}

fun <T> Iterable<T>.sizeAtLeast(size : Int) : Boolean {
    if (this is List) {
        return this.size >= size
    }

    var currSize = 0
    val iter = iterator()
    while (currSize < size && iter.hasNext()) {
        currSize++
    }

    return currSize >= size
}

fun String?.lazySplit(separator: Char) : Iterable<String> {
    return lazySplit(separator, { it })
}

fun <T> String?.lazySplit(separator: Char, transform: (String) -> T) : Iterable<T> {
    if (this == null) {
        return emptyList()
    }

    return object : Iterable<T> {
        override fun iterator(): Iterator<T> {
            return object : Iterator<T> {
                var currIndex = 0

                override fun hasNext(): Boolean {
                    return currIndex < this@lazySplit.length - 1
                }

                override fun next(): T {
                    val oldIndex = currIndex
                    var index = currIndex
                    val maxIndex = this@lazySplit.length - 1
                    while (index <= maxIndex) {
                        if (this@lazySplit[index] == separator) {
                            break
                        }

                        index++
                    }

                    currIndex = index + 1
                    return transform(substring(oldIndex, index))
                }
            }
        }
    }
}