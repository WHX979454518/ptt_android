package com.xianzhitech.ptt.ui.home

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.describeInHumanMessage
import com.xianzhitech.ptt.ui.app.AboutActivity
import com.xianzhitech.ptt.ui.app.FeedbackActivity
import com.xianzhitech.ptt.ui.app.ShareActivity
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import com.xianzhitech.ptt.ui.map.OfflineMapDownloadFragment
import com.xianzhitech.ptt.ui.settings.SettingsActivity
import com.xianzhitech.ptt.ui.user.EditProfileActivity
import com.xianzhitech.ptt.ui.widget.ModelView
import io.reactivex.Completable
import io.reactivex.CompletableObserver
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
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
            findView<Button>(R.id.profile_offline_map).apply {
                setOnClickListener(this@ProfileFragment)
                setCompoundDrawablesWithIntrinsicBounds(
                        context.getTintedDrawable(R.drawable.ic_map, tintColor),
                        null, null, null)

            }
            findView<View>(R.id.profile_logout).setOnClickListener(this@ProfileFragment)


            views = Views(this)
        }
    }

    override fun onStart() {
        super.onStart()

        appComponent.signalBroker.currentUser
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    views?.iconView?.model = it.orNull()
                    views?.nameView?.text = it.orNull()?.name
                    views?.numberView?.text = it.orNull()?.id
                }
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        when (fragment.tag) {
            TAG_LOGOUT_CONFIRMATION -> {
                appComponent.signalBroker.logout()
                callbacks<Callbacks>()!!.onLoggedOut()
            }
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

            map["userId"] = RequestBody.create(plainText, appComponent.signalBroker.peekUserId() ?: "unknown")
            map["model"] = RequestBody.create(plainText, "${Build.MANUFACTURER} - ${Build.MODEL}")

            dst.listFiles()?.forEachIndexed { _, file ->
                map["log_file\"; filename=\"${file.name}"] = RequestBody.create(plainText, file)
            }

            appComponent.appApi.submitLogs(map)
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(LogUploadSubscriber(activity.applicationContext))
    }

    private class LogUploadSubscriber(private val appContext: Context) : CompletableObserver {

        override fun onSubscribe(d: Disposable?) {}

        override fun onError(e: Throwable?) {
            Toast.makeText(appContext, e.describeInHumanMessage(appContext), Toast.LENGTH_LONG).show()
            logger.e(e) { "Error uploading logs" }
        }

        override fun onComplete() {
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
            R.id.profile_offline_map -> activity.startActivityWithAnimation(FragmentDisplayActivity.createIntent(
                    OfflineMapDownloadFragment::class.java
            ))
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
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (count, totalFileSize) ->
                    if (count == 0 || totalFileSize == 0L) {
                        Toast.makeText(context, R.string.no_upload, Toast.LENGTH_LONG).show()
                    } else {
                        AlertDialogFragment.Builder().apply {
                            val sizeKB = Math.round(totalFileSize / 1024f)
                            message = getString(R.string.total_log_files, count, "$sizeKB KB")
                            btnPositive = getString(R.string.upload)
                            btnNegative = getString(R.string.dialog_cancel)
                        }.show(childFragmentManager, TAG_UPLOAD_LOG_CONFIRMATION)
                    }
                }
                .bindToLifecycle()
    }


    private class Views(rootView: View,
                        val iconView: ModelView = rootView.findView(R.id.profile_icon),
                        val nameView: TextView = rootView.findView(R.id.profile_name),
                        val numberView: TextView = rootView.findView(R.id.profile_number))

    interface Callbacks {
        fun onLoggedOut()
    }

    companion object {
        private const val TAG_LOGOUT_CONFIRMATION = "tag_logout_confirmation"
        private const val TAG_UPLOAD_LOG_CONFIRMATION = "tag_log_confirmation"
    }
}
