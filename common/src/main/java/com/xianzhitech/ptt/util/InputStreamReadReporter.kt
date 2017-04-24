package com.xianzhitech.ptt.util

import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong


class InputStreamReadReporter(private val inputStream: InputStream,
                              private val callback: (Long) -> Unit) : InputStream() {
    private val readBytes = AtomicLong(0)

    override fun read(): Int {
        return inputStream.read().apply {
            callback(readBytes.incrementAndGet())
        }
    }

    override fun read(b: ByteArray?): Int {
        return inputStream.read(b).apply {
            if (this > 0) {
                callback(readBytes.addAndGet(this.toLong()))
            }
        }
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return inputStream.read(b, off, len).apply {
            if (this > 0) {
                callback(readBytes.addAndGet(this.toLong()))
            }
        }
    }

    override fun skip(n: Long): Long {
        return inputStream.skip(n).apply {
            if (this > 0) {
                callback(readBytes.addAndGet(this))
            }
        }
    }

    override fun available(): Int {
        return inputStream.available()
    }

    override fun reset() {
        inputStream.reset()
        readBytes.set(0L)
    }

    override fun close() {
        inputStream.close()
    }

    override fun mark(readlimit: Int) {
        inputStream.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return inputStream.markSupported()
    }
}