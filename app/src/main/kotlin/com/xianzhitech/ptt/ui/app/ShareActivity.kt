package com.xianzhitech.ptt.ui.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.xianzhitech.ptt.BuildConfig
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity


class ShareActivity : BaseToolbarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_share)

        val barcodeView : ImageView = findView(R.id.share_barCode)

        val downloadUrl = "${BuildConfig.APP_SERVER_ENDPOINT}/app/latest/"

        Glide.with(this)
            .load(Uri.parse("http://qr.liantu.com/api.php?&bg=ffffff&fg=000000")
                    .buildUpon()
                    .appendQueryParameter("w", barcodeView.layoutParams.width.toString())
                    .appendQueryParameter("text", downloadUrl)
                    .build())
            .into(barcodeView)

        findViewById(R.id.share_btn).setOnClickListener {
            startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, R.string.share_text.toFormattedString(this, downloadUrl.toString())),
                    R.string.share_via.toFormattedString(this)
            ))
        }
    }
}