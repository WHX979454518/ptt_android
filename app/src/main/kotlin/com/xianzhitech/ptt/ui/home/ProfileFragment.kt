package com.xianzhitech.ptt.ui.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.trello.rxlifecycle.FragmentEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.currentUserID
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.app.AboutActivity
import com.xianzhitech.ptt.ui.app.FeedbackActivity
import com.xianzhitech.ptt.ui.app.ShareActivity
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.settings.SettingsActivity
import com.xianzhitech.ptt.ui.user.EditProfileActivity
import com.xianzhitech.ptt.ui.widget.drawable.createDrawable
import okhttp3.MediaType
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import rx.Completable
import rx.Single
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File

private val logger = LoggerFactory.getLogger("ProfileFragment")

class ProfileFragment : BaseFragment(), View.OnClickListener, AlertDialogFragment.OnPositiveButtonClickListener {
    private var views: Views? = null
    private lateinit var appComponent: AppComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appComponent = context.applicationContext as AppComponent
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_profile, container, false)?.apply {
            val tintColor = context.getColorCompat(R.color.grey_700)
            findView<Button>(R.id.profile_settings).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_settings_black, tintColor),
                        null, null, null)
            }
            findView<Button>(R.id.profile_edit).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_mode_edit_black, tintColor),
                        null, null, null)
            }
            findView<Button>(R.id.profile_feedback).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_feedback_black, tintColor),
                        null, null, null)
            }
            findView<Button>(R.id.profile_about).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_info_black, tintColor),
                        null, null, null)
            }
            findView<Button>(R.id.profile_share).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_share, tintColor),
                        null, null, null)
            }
            findView<Button>(R.id.profile_logUpload).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_file_upload_black, tintColor),
                        null, null, null)
            }
            findView<View>(R.id.profile_logout).setOnClickListener(this@ProfileFragment)

            views = Views(this).apply {

                appComponent.signalHandler.loginState
                        .filter { it.currentUserID != null }
                        .switchMap { appComponent.userRepository.getUser(it.currentUserID!!).observe() }
                        .compose(bindUntil(FragmentEvent.DESTROY_VIEW))
                        .observeOnMainThread()
                        .subscribeSimple {
                            iconView.setImageDrawable(it?.createDrawable(context))
                            nameView.text = it?.name
                            numberView.text = it?.id
                        }
            }
        }
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_LOGOUT_CONFIRMATION -> appComponent.signalHandler.logout()
            TAG_UPLOAD_LOG_CONFIRMATION -> handleLogUpload()
        }
    }

    private fun handleLogUpload() {
        val app = activity.application
        val srcDir = File(app.filesDir, "log")
        Completable.defer {
            // Moving log files to cache
            val dst = File(app.cacheDir, "log")
            dst.mkdirs()
            dst.deleteRecursively()
            srcDir.copyRecursively(dst, true)

            val plainText = MediaType.parse("text/plain")
            val appComponent = app as AppComponent

            val map = hashMapOf<String, RequestBody>()

            map["userId"] = RequestBody.create(plainText, appComponent.signalHandler.currentUserId ?: "unknown")
            map["model"] = RequestBody.create(plainText, "${Build.MANUFACTURER} - ${Build.MODEL}")

            dst.listFiles()?.forEachIndexed { i, file ->
                map["log_file\"; filename=\"${file.name}"] = RequestBody.create(plainText, file)
            }

            appComponent.appService.submitLogs(map)
        }.subscribeOn(Schedulers.io())
                .doOnCompleted {
                    srcDir.listFiles()?.forEach {
                        it.writeBytes(byteArrayOf())
                    }
                }
                .observeOnMainThread()
                .subscribe(LogUploadSubscriber(activity.applicationContext))
    }

    private class LogUploadSubscriber(private val appContext: Context) : Completable.CompletableSubscriber {
        override fun onSubscribe(d: Subscription?) { }

        override fun onError(e: Throwable?) {
            Toast.makeText(appContext, e.describeInHumanMessage(appContext), Toast.LENGTH_LONG).show()
            logger.e(e) { "Error uploading logs" }
        }

        override fun onCompleted() {
            Toast.makeText(appContext, R.string.log_upload_success, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.profile_logout -> {
                AlertDialogFragment.Builder().apply {
                    message = R.string.log_out_confirm_message.toFormattedString(context)
                    btnPositive = R.string.logout.toFormattedString(context)
                    btnNegative = R.string.dialog_cancel.toFormattedString(context)
                }.show(childFragmentManager, TAG_LOGOUT_CONFIRMATION)
            }

            R.id.profile_settings -> activity.startActivityWithAnimation(Intent(context, SettingsActivity::class.java))
            R.id.profile_edit -> activity.startActivityWithAnimation(Intent(context, EditProfileActivity::class.java))
            R.id.profile_feedback -> activity.startActivityWithAnimation(Intent(context, FeedbackActivity::class.java))
            R.id.profile_about -> activity.startActivityWithAnimation(Intent(context, AboutActivity::class.java))
            R.id.profile_share -> activity.startActivityWithAnimation(Intent(context, ShareActivity::class.java))
            R.id.profile_logUpload -> confirmLogUpload()
        }
    }

    private fun confirmLogUpload() {
        val app = activity.application
        Single.fromCallable<Pair<Int, Long>> {
            val initialValue = 0 to 0L
            // Counting log files
            File(app.filesDir, "log").listFiles()
                    ?.fold(initialValue, { result, file -> result.first + 1 to result.second + file.length() }) ?: initialValue
        }.subscribeOn(Schedulers.io())
                .subscribeOn(AndroidSchedulers.mainThread())
                .toObservable()
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    val (count, totalFileSize) = it
                    if (count == 0 || totalFileSize == 0L) {
                        Toast.makeText(context, R.string.no_upload, Toast.LENGTH_LONG).show()
                    }
                    else {
                        AlertDialogFragment.Builder().apply {
                            val sizeKB = Math.round(totalFileSize / 1024f)
                            message = getString(R.string.total_log_files, count, "$sizeKB KB")
                            btnPositive = getString(R.string.upload)
                            btnNegative = getString(R.string.dialog_cancel)
                        }.show(childFragmentManager, TAG_UPLOAD_LOG_CONFIRMATION)
                    }
                }
    }


    private class Views(rootView: View,
                        val iconView: ImageView = rootView.findView(R.id.profile_icon),
                        val nameView: TextView = rootView.findView(R.id.profile_name),
                        val numberView: TextView = rootView.findView(R.id.profile_number))

    companion object {
        private const val TAG_LOGOUT_CONFIRMATION = "tag_logout_confirmation"
        private const val TAG_UPLOAD_LOG_CONFIRMATION = "tag_log_confirmation"
    }
}
