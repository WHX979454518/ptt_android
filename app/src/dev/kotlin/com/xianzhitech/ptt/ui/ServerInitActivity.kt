package com.xianzhitech.ptt.ui

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import rx.Observable
import rx.schedulers.Schedulers
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.Executors


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
        endpointView.setText(BuildConfig.SIGNAL_SERVER_ENDPOINT)
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
                val okHttp = OkHttpClient.Builder().cookieJar(cookieJar).addNetworkInterceptor(StethoInterceptor()).build()
                val service = Retrofit.Builder()
                        .client(okHttp)
                        .baseUrl(endpoint)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(MaintainService::class.java)

                service.init().execute()

                subscriber += "Logging as admin"
                service.login(LoginRequest("0", "000000")).execute()

                subscriber += "Creating enterprise..."
                val enterpriseNumber = service.createEnterpriseNumber().execute().body().number
                subscriber += "Created enterprise number $enterpriseNumber"

                val request = EnterpriseCreateRequest("测试企业", enterpriseNumber, "000000", EnterpriseQuota(10000, 10000))
                service.createEnterprise(request).execute()
                subscriber += "Created enterprise $request"

                cookieJar.clear()

                subscriber += "Logging as ${request.name}"
                service.login(LoginRequest(enterpriseNumber, request.password)).execute()

                val users = resources.getStringArray(R.array.user_names)
                subscriber += "Creating ${users.size} users..."

                val createdUserNumbers = arrayListOf<String?>()
                val tasks = arrayListOf<Observable<String>>()
                val scheduler = Schedulers.from(Executors.newFixedThreadPool(10))

                users.forEachIndexed { i, s ->
                    tasks.add(Observable.fromCallable {
                        val userNumber = service.createUserNumber().execute().body().number
                        service.createUser(CreateUserRequest(s, userNumber, (13000000000L + i).toString(), "000000", UserPrivileges((1 + 6 * Math.random()).toInt()))).execute()
                        userNumber
                    }.subscribeOn(scheduler).onErrorReturn { null })
                }

                var lastProgress = 0
                Observable.merge(tasks).toBlocking().forEach {
                    val progress = createdUserNumbers.size * 100 / tasks.size
                    if (progress - lastProgress >= 10 || progress == 100) {
                        subscriber += "$progress%% completed creating users..."
                        lastProgress = progress
                    }
                    createdUserNumbers.add(it)
                }

                subscriber += "Created ${users.size} users"

                val categories = arrayOf(10, 20, 30, 50, 100, 200, 300)

                createdUserNumbers.removeAll { it == null }
                createdUserNumbers.sortBy { it!!.toInt() }

                categories.forEach { category ->
                    val createSize = Math.min(createdUserNumbers.size, category)
                    val groupRequest = CreateGroupRequest("${createSize}人组", "这是一个${createSize}人组", createdUserNumbers.subList(0, createSize) as List<String>)
                    service.createGroup(groupRequest).execute()
                    subscriber.onNext("Created group ${groupRequest.name}")
                }

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
                .subscribe(object : GlobalSubscriber<String>() {
                    override fun onNext(t: String) {
                        adapter.add(t)
                    }

                    override fun onCompleted() {
                        super.onCompleted()
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