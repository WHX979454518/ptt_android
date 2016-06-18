package com.xianzhitech.ptt.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xianzhitech.ptt.ext.appComponent
import java.io.File

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null) {
            val lastUpdateDownloadId = context.appComponent.preference.updateDownloadId
            if (lastUpdateDownloadId != null && lastUpdateDownloadId.second == intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)) {
                installPackage(context, lastUpdateDownloadId.second)
                context.appComponent.preference.updateDownloadId = null
            }
        }
    }
}

fun installPackage(context: Context, downloadQueueId: Long): Boolean {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    downloadManager.query(DownloadManager.Query().apply { setFilterById(downloadQueueId) })?.use {
        if (it.moveToFirst() && it.getInt(it.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
            context.startActivity(Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.fromFile(File(it.getString(it.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)))), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
    }

    return false
}