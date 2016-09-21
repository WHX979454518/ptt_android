package com.xianzhitech.ptt.service

import com.xianzhitech.ptt.ext.transform
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.User
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import rx.Single
import rx.schedulers.Schedulers


data class SyncContactResult(val version : Long,
                             val users : List<User>,
                             val groups : List<Group>) {
    override fun toString(): String {
        return "SyncContactResult{version=$version, userCount=${users.size}, groupCount=${groups.size}}"
    }
}

interface CoreService {
    fun syncContact(userId: String, version : Long) : Single<SyncContactResult?>
}

class CoreServiceImpl(private val okHttpClient: OkHttpClient,
                      private val endpoint : String) : CoreService {


    override fun syncContact(userId : String, version: Long): Single<SyncContactResult?> {
        return Single.fromCallable {
            val req = Request.Builder()
                    .url("$endpoint/api/contact/sync/$userId/$version")
                    .get()
                    .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.code() == 304) {
                return@fromCallable null
            }

            if (resp.isSuccessful.not()) {
                throw RuntimeException("Error executing sync contact call: code = ${resp.code()}, msg = ${resp.body().string()}")
            }

            val result = JSONObject(resp.body().string())
            SyncContactResult(version = result.getLong("version"),
                    users = result.optJSONArray("enterpriseMembers")?.transform { UserObject(it as JSONObject) } ?: emptyList(),
                    groups = result.optJSONArray("enterpriseGroups")?.transform { GroupObject(it as JSONObject) } ?: emptyList()
            )
        }.subscribeOn(Schedulers.io())
    }

}