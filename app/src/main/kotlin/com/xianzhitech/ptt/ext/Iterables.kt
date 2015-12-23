package com.xianzhitech.ptt.ext


fun <T, R> Iterable<T>.transform(mapper: (T) -> R) = object : Iterable<R> {
    override fun iterator(): Iterator<R> {
        return object : Iterator<R> {
            val origIterator = this@transform.iterator()

            override fun next() = mapper(origIterator.next())
            override fun hasNext() = origIterator.hasNext()
        }
    }

}