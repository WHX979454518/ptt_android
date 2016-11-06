package com.xianzhitech.ptt.ui.app

import android.content.DialogInterface
import android.os.Bundle
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
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.DevApp
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.setVisible
import java.net.URL

class AboutActivity : BaseAboutActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById(R.id.logo)?.setOnLongClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("选择一个环境")
            val environments = gatherSelectableEnvironments()
            val recyclerView = RecyclerView(this)
            recyclerView.layoutManager = LinearLayoutManager(this)
            val adapter = EnvironmentAdapter(environments, environments.indexOfFirst { it.url == appComponent.appServerEndpoint })
            recyclerView.adapter = adapter

            builder.setView(recyclerView)

            builder.setPositiveButton("确定", { dialogInterface: DialogInterface, i: Int ->
                dialogInterface as AlertDialog
                val selectedEnv = environments[adapter.selectedIndex]
                if (selectedEnv.isValid().not()) {
                    Toast.makeText(this, "URL格式不正确", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                (appComponent as DevApp).appServerEndpoint = selectedEnv.url
                appComponent.preference.lastIgnoredUpdateUrl = null
                appComponent.preference.lastAppParams = null
                appComponent.preference.contactVersion = -1
                Toast.makeText(this, "服务器已经设置为 ${selectedEnv.name} (${selectedEnv.url}), 重新登陆后生效", Toast.LENGTH_LONG).show()
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

    }


    private fun gatherSelectableEnvironments() : List<Environment> {
        val result = arrayListOf<Environment>(Environment.PROD)

        if (Environment.TEST.url != Environment.PROD.url) {
            result.add(Environment.TEST)
        }

        val currentEndpoint = appComponent.appServerEndpoint
        val otherEnvUrl : String = if (result.indexOfFirst { it.url == appComponent.appServerEndpoint } >= 0) {
            "http://192.168.2.2:8000"
        } else {
            currentEndpoint
        }

        result.add(Environment("其它环境", otherEnvUrl, true))
        return result
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