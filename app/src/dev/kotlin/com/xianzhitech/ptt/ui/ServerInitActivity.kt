package com.xianzhitech.ptt.ui

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getString
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.plusAssign
import com.xianzhitech.ptt.maintain.service.*
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import rx.Observable
import rx.Subscriber
import rx.schedulers.Schedulers
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class ServerInitActivity : BaseToolbarActivity() {
    private val adapter = Adapter()
    private lateinit var recyclerView : RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_server_init)
        recyclerView = findView(R.id.serverInit_logView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val endpointView = findView<EditText>(R.id.serverInit_name)
        endpointView.setText("")
        endpointView.selectAll()

        findViewById(R.id.serverInit_ok)?.setOnClickListener {
            start(endpointView.getString(), it)
        }
    }

    private fun start(endpoint: String, button: View) {
        button.isEnabled = false
        adapter.clear()

        Observable.create<String> { subscriber ->
            try {
                subscriber += "Start using $endpoint"

                val cookieCache = SetCookieCache()
                val cookieJar = PersistentCookieJar(cookieCache, SharedPrefsCookiePersistor(applicationContext))
                val okHttp = OkHttpClient.Builder().cookieJar(cookieJar).readTimeout(0, TimeUnit.SECONDS).build()
                val service = Retrofit.Builder()
                        .client(okHttp)
                        .baseUrl(endpoint)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(MaintainService::class.java)

                cookieJar.clear()
                service.init().execute()
                service.login(LoginRequest("0", "000000")).execute()

                val enterprise = Enterprise("测试企业", "000000", EnterpriseQuota(10000, 10000))
                val users = resources.getStringArray(R.array.user_names).mapIndexed { i, s ->
                    User(s, (13000000000L + i).toString(), "000000", UserPrivileges((1 + 6 * Math.random()).toInt()))
                }

                val groups = arrayOf(10, 20, 30, 50, 100, 200, 300).map { memberSize ->
                    val createSize = Math.min(users.size, memberSize)
                    Group("${createSize}人组", "这是一个${createSize}人组", users.subList(0, createSize).mapIndexed { i, user -> i.toString() })
                }

                val response = service.import(ImportRequest(enterprise = enterprise, users = users, groups = groups)).execute().body()
                subscriber.onNext("导入成功。企业ID = ${response.enterpriseId}")

            } catch(e: Exception) {
                subscriber.onError(e)
            }
            subscriber.onCompleted()
        }.subscribeOn(Schedulers.io())
                .onErrorReturn {
                    Log.e("ServerInit", "Error", it)
                    val os = ByteArrayOutputStream()
                    it.printStackTrace(PrintStream(os, true, "UTF-8"))
                    String(os.toByteArray(), Charsets.UTF_8)
                }
                .observeOnMainThread()
                .subscribe(object : Subscriber<String>() {
                    override fun onNext(t: String) {
                        adapter.add(t)
                    }

                    override fun onError(e: Throwable?) { }

                    override fun onCompleted() {
                        button.isEnabled = true
                    }
                })
    }

    private class ViewHolder(parent : ViewGroup,
                             val textView : TextView = TextView(parent.context)) : RecyclerView.ViewHolder(textView)

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        private val items = arrayListOf<String>()

        fun add(format: String, vararg args : String) {
            items.add(format.format(*args))
            notifyItemInserted(items.size)
            recyclerView.smoothScrollToPosition(items.size - 1)
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = items[position]
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder? {
            return ViewHolder(parent)
        }

        fun clear() {
            val oldSize = items.size
            items.clear()
            notifyItemRangeRemoved(0, oldSize)
        }
    }
}