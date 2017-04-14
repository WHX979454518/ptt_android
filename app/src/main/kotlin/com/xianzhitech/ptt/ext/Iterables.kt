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

fun <T> Iterable<T>.first(count: Int): List<T> {
    if (this is List) {
        return subList(0, Math.min(count - 1, size - 1))
    } else {
        return mapFirst(count, { it })
    }
}

inline fun <T, R> Iterable<T>.mapFirst(count: Int, transform: (T) -> R): List<R> {
    val result = ArrayList<R>(count)
    var index = 0
    val iter = iterator()
    while (index < count && iter.hasNext()) {
        result.add(transform(iter.next()))
        index++
    }
    return result
}

fun <T> Iterable<T>.sizeAtLeast(size: Int): Boolean {
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

fun String?.lazySplit(separator: Char): Collection<String> {
    if (this == null) {
        return emptyList()
    }

    return object : AbstractCollection<String>() {
        override val size: Int by lazy { this@lazySplit.count { it == separator } + 1 }

        override fun contains(element: String): Boolean {
            val index = indexOf(element)
            if (index < 0) {
                return false
            }

            // 必须前一个或者后一个是分隔符才能算是找到
            if ((index == 0 || get(index - 1) == separator) &&
                    (index == this@lazySplit.length - 1 || get(index + 1) == separator)) {
                return true
            }

            return false
        }

        override fun isEmpty(): Boolean {
            return this@lazySplit.isEmpty()
        }

        override fun iterator(): MutableIterator<String> {
            return object : MutableIterator<String> {
                var currIndex = 0

                override fun remove() {
                    throw UnsupportedOperationException()
                }

                override fun hasNext(): Boolean {
                    return currIndex < this@lazySplit.length - 1
                }

                override fun next(): String {
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
                    return substring(oldIndex, index)
                }
            }

        }
    }
}

/**
 * returns true if collection can be added
 */
inline fun <T> MutableCollection<T>.addAllLimited(maxSize : Int, input : Iterable<T>, filter : (T?) -> Boolean) : Boolean {
    if (size < maxSize) {
        val iter = input.iterator()
        while (iter.hasNext()) {
            val obj = iter.next()
            if (filter(obj)) {
                add(obj)
            }

            if (size >= maxSize) {
                return false
            }
        }

        return true
    }

    return false
}

fun <E> List<E>.atMost(count: Int): List<E> {
    return subList(0, Math.min(count, size))
}

fun <E> Collection<E>.without(element: E?): List<E> {
    if (element != null) {
        return filterNot { it == element }
    }

    return toList()
}