package com.xianzhitech.ptt.ui.base

import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.trello.rxlifecycle.ActivityEvent
import com.trello.rxlifecycle.RxLifecycle
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.ui.home.AlertDialogFragment
import com.xianzhitech.ptt.update.installPackage
import rx.Observable
import rx.subjects.BehaviorSubject
import java.io.Serializable

abstract class BaseActivity : AppCompatActivity(),
        AlertDialogFragment.OnPositiveButtonClickListener,
        AlertDialogFragment.OnNeutralButtonClickListener {

    private class JoinRoomState(val createRoomRequest: CreateRoomRequest?,
                                var roomId : String?) : Serializable

    private val lifecycleEventSubject = BehaviorSubject.create<ActivityEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleEventSubject.onNext(ActivityEvent.CREATE)
    }

    override fun onDestroy() {
        lifecycleEventSubject.onNext(ActivityEvent.DESTROY)

        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        lifecycleEventSubject.onNext(ActivityEvent.START)
    }

    override fun onStop() {
        lifecycleEventSubject.onNext(ActivityEvent.STOP)

        super.onStop()
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        if (fragment.tag == UPDATE_DIALOG_TAG) {
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadUri = Uri.parse(fragment.attachment as String)
            val preference = (application as AppComponent).preference
            val lastUpdateDownloadId = preference.updateDownloadId
            if (lastUpdateDownloadId == null || lastUpdateDownloadId.first != downloadUri) {
                if (lastUpdateDownloadId != null) {
                    downloadManager.remove(lastUpdateDownloadId.second)
                }

                val downloadRequest = DownloadManager.Request(downloadUri).apply {
                    setMimeType("application/vnd.android.package-archive")
                    setNotificationVisibility(View.VISIBLE)
                    setVisibleInDownloadsUi(false)
                    setTitle(R.string.app_updating.toFormattedString(this@BaseActivity, R.string.app_name.toFormattedString(this@BaseActivity)))
                }
                preference.updateDownloadId = Pair(downloadUri, downloadManager.enqueue(downloadRequest))
            }
            else if (lastUpdateDownloadId != null) {
                installPackage(this, lastUpdateDownloadId.second)
            }
        }
    }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) {
        if (fragment.tag == UPDATE_DIALOG_TAG) {
            fragment.dismiss()
        }
    }

    fun joinRoom(roomId: String) {

    }

    fun joinRoom(createRoomRequest: CreateRoomRequest) {

    }

    override fun onResume() {
        super.onResume()

        lifecycleEventSubject.onNext(ActivityEvent.RESUME)
    }

    override fun onPause() {
        lifecycleEventSubject.onNext(ActivityEvent.PAUSE)

        super.onPause()
    }

    fun <D> bindToLifecycle(): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindActivity<D>(lifecycleEventSubject)
    }

    fun <D> bindUntil(event: ActivityEvent): Observable.Transformer<in D, out D> {
        return RxLifecycle.bindUntilActivityEvent<D>(lifecycleEventSubject, event)
    }

    companion object {
        const val UPDATE_DIALOG_TAG = "tag_update_dialog"
    }
}
