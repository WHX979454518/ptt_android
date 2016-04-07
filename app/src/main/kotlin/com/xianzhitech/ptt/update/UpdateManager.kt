package com.xianzhitech.ptt.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import rx.Observable
import java.io.File

data class UpdateInfo(val updateMessage: CharSequence,
                      val updateUrl : Uri,
                      val forceUpdate : Boolean)

interface UpdateManager {
    fun retrieveUpdateInfo() : Observable<UpdateInfo?>
}

fun installPackage(context: Context, downloadQueueId : Long) : Boolean {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    downloadManager.query(DownloadManager.Query().apply { setFilterById(downloadQueueId)})?.use {
        if (it.moveToFirst() && it.getInt(it.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
            context.startActivity(Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.fromFile(File(it.getString(it.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)))), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }
    }

    return false
}