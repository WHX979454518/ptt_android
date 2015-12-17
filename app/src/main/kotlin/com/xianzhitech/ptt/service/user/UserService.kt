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
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.service.provider.AuthProvider
import com.xianzhitech.ptt.service.provider.SignalProvider
import com.xianzhitech.ptt.ui.util.RxUtil
import hugo.weaving.DebugLog
import rx.Observable
import rx.Subscriber
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

/**
 *
 * 用户鉴权, 通讯录的系统服务
 *
 * Created by fanchao on 17/12/15.
 */

class UserService() : Service() {
    companion object {
        public const val ACTION_LOGIN = "action_login"
        public const val ACTION_LOGOUT = "action_logout";

        public const val ACTION_LOGIN_STATUS_CHANGED = "action_lsc"

        public const val ACTION_USER_LOGON_FAILED = "action_user_logon_failed"

        public const val EXTRA_LOGIN_USERNAME = "extra_name"
        public const val EXTRA_LOGIN_PASSWORD = "extra_pass"

        public const val EXTRA_LOGON_USER = "extra_user"
        public const val EXTRA_LOGIN_STATUS = "extra_login_status"
        public const val EXTRA_LOGON_FAILED_REASON = "extra_failed_reason"

        private const val NOTIFICATION_ID = 1

        public @JvmStatic fun buildEmptyIntent(context : Context) = Intent(context, UserService::class.java)

        public @JvmStatic fun buildLoginIntent(context : Context, name : String, password : String) =
                buildEmptyIntent(context)
                        .setAction(ACTION_LOGIN)
                        .putExtra(EXTRA_LOGIN_USERNAME, name).putExtra(EXTRA_LOGIN_PASSWORD, password)

        public @JvmStatic fun buildLogoutIntent(context : Context) =
                buildEmptyIntent(context)
                        .setAction(ACTION_LOGOUT)

        public @JvmStatic fun getLoginStatus(context: Context) = Observable.merge(
                RxUtil.fromBroadcast(context, ACTION_LOGIN_STATUS_CHANGED).map { it.getIntExtra(EXTRA_LOGIN_STATUS, LoginStatus.IDLE) },
                RxUtil.fromService<UserServiceBinder>(context, buildEmptyIntent(context), Context.BIND_AUTO_CREATE).map { it.loginStatus })
    }

    private val binder = lazy { LocalUserServiceBinder(this) }

    var logonUser : Person? = null
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
        signalProvider = appComponent.providesSignal()
        authProvider = appComponent.providesAuth()

        val savedUserCredentials = getSavedUserCredentials()
        if (savedUserCredentials != null) {
            doLogin(savedUserCredentials.first, savedUserCredentials.second)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        handleIntent(intent);
        return binder.value
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
            ACTION_LOGIN -> doLogin(intent.getStringExtra(EXTRA_LOGIN_USERNAME), intent.getStringExtra(EXTRA_LOGIN_PASSWORD))
            ACTION_LOGOUT -> doLogout()
            else -> throw IllegalArgumentException("Unknown action ${intent.action}")
        }
    }

    @DebugLog
    private fun doLogout() {
        cancelLogin()
        clearUserCredentials()

        if (logonUser != null) {
            stopForeground(true)
        }
    }

    @DebugLog
    private fun cancelLogin() {
        loginSubscription?.unsubscribe()
        loginSubscription = null
    }

    @DebugLog
    private fun doLogin(userName: String, password: String) {
        cancelLogin()

        NotificationCompat.Builder(this)
                .setContentText(getText(R.string.app_name))

        startForeground(NOTIFICATION_ID, foregroundNotification)

        loginSubscription = authProvider.login(userName, password)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : UserServiceSubscriber<Person>() {
                    override fun onNext(t: Person) {
                        logonUser = t

                        saveUserCredentials(userName, password)

                        notifyLoginStatusChanged()
                    }

                    override fun onError(e: Throwable?) {
                        logonUser = null
                        clearUserCredentials()
                        sendBroadcast(Intent(ACTION_USER_LOGON_FAILED).putExtra(EXTRA_LOGON_FAILED_REASON, e))
                        stopForeground(true)
                    }

                    override fun onCompleted() {
                        loginSubscription = null
                        notifyLoginStatusChanged()
                    }
                })
    }

    //TODO: 加密用户数据
    fun saveUserCredentials(userName: String, password: String) {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putString("username", userName).putString("password", password).apply()
    }

    fun clearUserCredentials() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().remove("username").remove("password").apply()
    }

    fun getSavedUserCredentials() : Pair<String, String>?{
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        if (!pref.contains("username") || !pref.contains("password")) {
            return null
        }
        return Pair(pref.getString("username", null), pref.getString("password", null))
    }

    fun notifyLoginStatusChanged() {
        sendBroadcast(Intent(ACTION_LOGIN_STATUS_CHANGED).putExtra(EXTRA_LOGIN_STATUS, loginStatus).putExtra(EXTRA_LOGON_USER, logonUser))
    }

    @LoginStatus
    val loginStatus : Int
        get() = when {
            logonUser != null -> LoginStatus.LOGGED_ON
            loginSubscription != null -> LoginStatus.LOGIN_IN_PROGRESS
            else -> LoginStatus.IDLE
        }
}

private open class UserServiceSubscriber<T>() : Subscriber<T>() {
    override fun onError(e: Throwable?) {
    }

    override fun onCompleted() {
    }

    override fun onNext(t: T) {
    }
}

private class LocalUserServiceBinder(private val service : UserService) : Binder(), UserServiceBinder {
    override val logonUser : Person?
            get() = service.logonUser

    override val loginStatus : Int
            get() = service.loginStatus
}