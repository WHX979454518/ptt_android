package com.xianzhitech.ptt

import android.app.Activity
import android.content.DialogInterface
import android.os.Handler
import android.preference.PreferenceManager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.setVisible
import com.xianzhitech.ptt.service.AppService
import com.xianzhitech.ptt.util.SimpleActivityLifecycleCallbacks
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import rx.schedulers.Schedulers
import java.net.URL

class DevApp : App() {
    companion object {
        private const val KEY_APP_SERVER = "test_app_server"
    }

    override val appService: AppService
        get() = Retrofit.Builder()
                .addCallAdapterFactory(RxJavaCallAdapterFactory.createWithScheduler(Schedulers.io()))
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(appServerEndpoint)
                .client(httpClient)
                .build()
                .create(AppService::class.java)

    override var appServerEndpoint: String
        get() = PreferenceManager.getDefaultSharedPreferences(this).getString(KEY_APP_SERVER, BuildConfig.APP_SERVER_ENDPOINT)
        set(value) {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(KEY_APP_SERVER, value)
                    .apply()
        }

    override fun onCreate() {
        super.onCreate()

        Stetho.initializeWithDefaults(this)

        registerActivityLifecycleCallbacks(object : SimpleActivityLifecycleCallbacks() {
            private val handler = Handler()

            override fun onActivityStarted(activity: Activity) {
                handler.postDelayed({
                    activity.findViewById(R.id.logo)?.setOnLongClickListener {
                        val builder = AlertDialog.Builder(activity)
                        builder.setTitle("选择一个环境")
                        val environments = gatherSelectableEnvironments()
                        val recyclerView = RecyclerView(activity)
                        recyclerView.layoutManager = LinearLayoutManager(activity)
                        val adapter = EnvironmentAdapter(environments, environments.indexOfFirst { it.url == appServerEndpoint })
                        recyclerView.adapter = adapter

                        builder.setView(recyclerView)

                        builder.setPositiveButton("确定", { dialogInterface: DialogInterface, i: Int ->
                            dialogInterface as AlertDialog
                            val selectedEnv = environments[adapter.selectedIndex]
                            if (selectedEnv.isValid().not()) {
                                Toast.makeText(activity, "URL格式不正确", Toast.LENGTH_LONG).show()
                                return@setPositiveButton
                            }

                            appServerEndpoint = selectedEnv.url
                            preference.lastIgnoredUpdateUrl = null
                            preference.lastAppParams = null
                            preference.contactVersion = -1
                            Toast.makeText(activity, "服务器已经设置为 ${selectedEnv.name} (${selectedEnv.url}), 重新登陆后生效", Toast.LENGTH_LONG).show()
                            dialogInterface.dismiss()
                        })

                        builder.setNeutralButton("取消", { dialogInterface: DialogInterface, i: Int ->
                            dialogInterface.dismiss()
                        })

                        builder.create().let {
                            it.show()
                            it.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                        }
                        true
                    }
                }, 1000)
            }
        })
    }

    private fun gatherSelectableEnvironments() : List<Environment> {
        val result = arrayListOf<Environment>(Environment.PROD)

        if (Environment.TEST.url != Environment.PROD.url) {
            result.add(Environment.TEST)
        }

        val currentEndpoint = appServerEndpoint
        val otherEnvUrl : String = if (result.indexOfFirst { it.url == appServerEndpoint } >= 0) {
            "http://192.168.2.2:8000"
        } else {
            currentEndpoint
        }

        result.add(Environment("其它环境", otherEnvUrl, true))
        return result
    }

    override fun onBuildHttpClient(): OkHttpClient.Builder {
        return super.onBuildHttpClient().addNetworkInterceptor(StethoInterceptor())
    }

    private data class Environment(val name : String,
                                   var url : String,
                                   val modifiable: Boolean) {
        companion object {
            @JvmStatic val PROD = Environment("生产环境", "http://netptt.cn:10005", false)
            @JvmStatic val TEST = Environment("测试环境", BuildConfig.APP_SERVER_ENDPOINT, false)
        }

        fun isValid() : Boolean {
            try {
                URL(url)
            } catch(e: Exception) {
                return false
            }
            return true
        }
    }

    private class EnvironmentAdapter(private val environments : List<Environment>,
                                     var selectedIndex : Int) : RecyclerView.Adapter<EnvironmentHolder>() {


        private val VIEW_TYPE_READ_ONLY: Int = 0
        private val VIEW_TYPE_CUSTOM: Int = 1

        override fun getItemViewType(position: Int): Int {
            return if (environments[position].modifiable) {
                VIEW_TYPE_CUSTOM
            } else {
                VIEW_TYPE_READ_ONLY
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EnvironmentHolder {
            return EnvironmentHolder(parent).apply {
                editView.setVisible(viewType == VIEW_TYPE_CUSTOM)
                labelView.setOnCheckedChangeListener { compoundButton, checked ->
                    if (adapterPosition >= 0 && settingCheckStateProgramtically.not() && selectedIndex != adapterPosition) {
                        selectedIndex = adapterPosition
                        notifyDataSetChanged()
                    }
                }
            }
        }

        override fun onBindViewHolder(holder: EnvironmentHolder, position: Int) {
            val env = environments[position]
            holder.env = env
            if (env.modifiable) {
                holder.labelView.text = env.name
                holder.editView.setText(env.url)
            } else {
                holder.labelView.text = "${env.name} (${env.url})"
            }
            holder.settingCheckStateProgramtically = true
            holder.labelView.isChecked = selectedIndex == position
            holder.settingCheckStateProgramtically = false
            holder.editView.isEnabled = position == selectedIndex
            if (env.modifiable && holder.editView.isEnabled) {
                holder.editView.requestFocusFromTouch()
            }
        }

        override fun getItemCount(): Int {
            return environments.size
        }
    }

    private class EnvironmentHolder(container : ViewGroup) :
            RecyclerView.ViewHolder(LayoutInflater.from(container.context).inflate(R.layout.view_environment, container, false)) {

        val labelView : RadioButton
        val editView : EditText
        var env : Environment? = null
        var settingCheckStateProgramtically = false

        init {
            labelView = itemView.findView(R.id.env_label)
            editView = itemView.findView(R.id.env_url)
            editView.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    env?.url = editView.text.toString()
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }
            })
        }
    }
}
