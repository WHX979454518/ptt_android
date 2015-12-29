package com.xianzhitech.ptt.service.user

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.v4.app.NotificationCompat
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.LoginResult
import com.xianzhitech.ptt.service.provider.SignalProvider
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.io.Serializable
import kotlin.text.isNullOrEmpty


/**
 *
 * 与用户鉴权服务的绑定接口
 *
 * Created by fanchao on 17/12/15.
 */
interface UserServiceBinder {

    /**
     * 当前已经登陆的用户
     */
    val logonUser: Person?

    /**
     * 当前登陆的状态
     */
    val loginStatus: LoginStatus
}

/**
 *
 * 用户鉴权, 通讯录的系统服务
 *
 * Created by fanchao on 17/12/15.
 */

class UserService() : Service(), UserServiceBinder {
    companion object {

        // ------------ 以下为接受的请求 -------------
        /**
         * 请求登陆. 用户名密码通过extra传入
         */
        public const val ACTION_LOGIN = "action_login"
        public const val EXTRA_LOGIN_USERNAME = "extra_name"
        public const val EXTRA_LOGIN_PASSWORD = "extra_pass"

        /**
         * 请求登出
         */
        public const val ACTION_LOGOUT = "action_logout";

        // ------------- 以下为发出的消息 -------------
        /**
         * 登陆状态发生变化的事件
         */
        public const val ACTION_LOGIN_STATUS_CHANGED = "action_lsc"

        /**
         * 登陆时出错的事件
         */
        public const val ACTION_USER_LOGON_FAILED = "action_user_logon_failed"

        /**
         * 出错的原因字符串
         */
        public const val EXTRA_LOGON_FAILED_REASON = "extra_failed_reason"

        private const val NOTIFICATION_ID = 1

        // -------------- 以下为辅助函数 ----------------
        public @JvmStatic fun buildEmpty(context: Context) = Intent(context, UserService::class.java)

        public @JvmStatic fun buildLogin(context: Context, name: String, password: String) =
                buildEmpty(context)
                        .setAction(ACTION_LOGIN)
                        .putExtra(EXTRA_LOGIN_USERNAME, name).putExtra(EXTRA_LOGIN_PASSWORD, password)

        public @JvmStatic fun buildLogout(context: Context) =
                buildEmpty(context)
                        .setAction(ACTION_LOGOUT)

        public @JvmStatic fun getLoginStatus(context: Context) =
                context.retrieveServiceValue(buildEmpty(context), { binder: UserServiceBinder -> binder.loginStatus }, AndroidSchedulers.mainThread(), ACTION_LOGIN_STATUS_CHANGED)

        public @JvmStatic fun getLogonUser(context: Context) =
                context.retrieveServiceValue(buildEmpty(context), { binder: UserServiceBinder -> binder.logonUser }, AndroidSchedulers.mainThread(), ACTION_LOGIN_STATUS_CHANGED)
                        .distinctUntilChanged()
    }

    private var binder: IBinder? = null

    override var logonUser: Person? = null
    var loginSubscription : Subscription? = null
    private lateinit var signalProvider : SignalProvider
    private lateinit var authProvider : AuthProvider
    val foregroundNotification : Notification by lazy {
        NotificationCompat.Builder(this)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.user_logon))
                .build()
    }

    override fun onCreate() {
        super.onCreate()

        val appComponent = application as AppComponent
        signalProvider = appComponent.signalProvider
        authProvider = appComponent.authProvider

        getSavedUserCredentials()?.let {
            doLogin(authProvider.resumeLogin(it))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancelLogin()
    }

    override fun onBind(intent: Intent?): IBinder? {
        handleIntent(intent);
        if (binder == null) {
            binder = LocalUserServiceBinder(this)
        }

        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleIntent(intent)
        return START_STICKY
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action.isNullOrEmpty()) {
            return
        }

        when (intent.action) {
            ACTION_LOGIN -> doLogin(authProvider.login(intent.getStringExtra(EXTRA_LOGIN_USERNAME), intent.getStringExtra(EXTRA_LOGIN_PASSWORD)))
            ACTION_LOGOUT -> doLogout()
        }
    }


    private fun doLogout() {
        cancelLogin()
        clearLoginToken()

        if (logonUser != null) {
            stopForeground(true)
        }

        stopSelf()
    }


    private fun cancelLogin() {
        loginSubscription?.unsubscribe()
        loginSubscription = null
    }


    private fun doLogin(loginResult: Observable<LoginResult>) {
        cancelLogin()

        NotificationCompat.Builder(this)
                .setContentText(getText(R.string.app_name))

        startForeground(NOTIFICATION_ID, foregroundNotification)

        loginSubscription = loginResult
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<LoginResult>() {
                    override fun onNext(t: LoginResult) {
                        logonUser = t.person

                        if (t.token != null) {
                            saveLoginToken(t.token)
                        } else {
                            clearLoginToken()
                        }

                        // Keep service running
                        startService(buildEmpty(this@UserService))
                        notifyLoginStatusChanged()
                    }

                    override fun onError(e: Throwable) {
                        logonUser = null
                        clearLoginToken()
                        sendBroadcast(Intent(ACTION_USER_LOGON_FAILED).putExtra(EXTRA_LOGON_FAILED_REASON, e))
                        stopForeground(true)
                    }

                    override fun onCompleted() {
                        loginSubscription = null
                        notifyLoginStatusChanged()
                    }
                })
    }

    fun saveLoginToken(token: Serializable) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("loginToken", token.serializeToBase64()).apply()
    }

    fun clearLoginToken() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().remove("loginToken").apply()
    }

    fun getSavedUserCredentials() =
            PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("loginToken", null)
                    .fromBase64ToSerializable()


    fun notifyLoginStatusChanged() {
        sendBroadcast(Intent(ACTION_LOGIN_STATUS_CHANGED))
    }

    override val loginStatus: LoginStatus
        get() = when {
            logonUser != null -> LoginStatus.LOGGED_ON
            loginSubscription != null -> LoginStatus.LOGIN_IN_PROGRESS
            else -> LoginStatus.IDLE
        }
}

private class LocalUserServiceBinder(private val service: UserService) : Binder(), UserServiceBinder by service {
}

enum class LoginStatus {
    IDLE,
    LOGIN_IN_PROGRESS,
    LOGGED_ON,
}