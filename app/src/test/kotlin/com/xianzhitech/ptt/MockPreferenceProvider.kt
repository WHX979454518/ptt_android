package com.xianzhitech.ptt

import com.xianzhitech.ptt.service.provider.PreferenceStorageProvider
import java.io.Serializable

/**
 * Created by fanchao on 9/01/16.
 */
class MockPreferenceProvider : PreferenceStorageProvider {
    val map = hashMapOf<String, Serializable?>()

    override fun save(key: String, value: Serializable?) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }

    override fun get(key: String) = map[key]
}