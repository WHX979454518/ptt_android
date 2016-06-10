package com.xianzhitech.ptt.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xianzhitech.ptt.AppComponent

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null) {
            val lastUpdateDownloadId = (context.applicationContext as AppComponent).preference.updateDownloadId
            if (lastUpdateDownloadId != null && lastUpdateDownloadId.second == intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)) {
                installPackage(context, lastUpdateDownloadId.second)
            }
        }
    }
}