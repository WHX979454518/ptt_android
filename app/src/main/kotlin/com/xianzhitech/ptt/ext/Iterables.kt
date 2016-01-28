package com.xianzhitech.ptt.ext

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