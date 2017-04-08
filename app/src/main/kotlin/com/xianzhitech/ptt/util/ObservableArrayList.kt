package com.xianzhitech.ptt.util

import android.databinding.ListChangeRegistry
import android.databinding.ObservableList


class ObservableArrayList<T> : android.databinding.ObservableArrayList<T>() {
    @Transient private var mListeners: ListChangeRegistry? = ListChangeRegistry()

    override fun addOnListChangedCallback(listener: ObservableList.OnListChangedCallback<*>) {
        if (mListeners == null) {
            mListeners = ListChangeRegistry()
        }
        mListeners!!.add(listener)
    }

    override fun removeOnListChangedCallback(listener: ObservableList.OnListChangedCallback<*>) {
        if (mListeners != null) {
            mListeners!!.remove(listener)
        }
    }

    override fun add(`object`: T): Boolean {
        super.add(`object`)
        notifyAdd(size - 1, 1)
        return true
    }

    override fun add(index: Int, `object`: T) {
        super.add(index, `object`)
        notifyAdd(index, 1)
    }

    override fun addAll(collection: Collection<T>): Boolean {
        val oldSize = size
        val added = super.addAll(collection)
        if (added) {
            notifyAdd(oldSize, size - oldSize)
        }
        return added
    }

    override fun addAll(index: Int, collection: Collection<T>): Boolean {
        val added = super.addAll(index, collection)
        if (added) {
            notifyAdd(index, collection.size)
        }
        return added
    }

    override fun clear() {
        val oldSize = size
        super.clear()
        if (oldSize != 0) {
            notifyRemove(0, oldSize)
        }
    }

    fun replaceAll(collection: Collection<T>) {
        clear()
        addAll(collection)
        mListeners?.notifyChanged(this)
    }

    override fun set(index: Int, `object`: T): T {
        val `val` = super.set(index, `object`)
        if (mListeners != null) {
            mListeners!!.notifyChanged(this, index, 1)
        }
        return `val`
    }

    override fun removeRange(fromIndex: Int, toIndex: Int) {
        super.removeRange(fromIndex, toIndex)
        notifyRemove(fromIndex, toIndex - fromIndex)
    }

    private fun notifyAdd(start: Int, count: Int) {
        mListeners?.notifyInserted(this, start, count)
    }

    private fun notifyRemove(start: Int, count: Int) {
        mListeners?.notifyRemoved(this, start, count)
    }

}