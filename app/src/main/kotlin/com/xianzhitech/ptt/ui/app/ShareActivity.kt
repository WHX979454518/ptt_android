package com.xianzhitech.ptt.ui.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity


class ShareActivity : BaseToolbarActivity() {
    private lateinit var barcodeView : ImageView
    private lateinit var shareButton : View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_share)

        barcodeView = findView(R.id.share_barCode)
        shareButton = findViewById(R.id.share_btn)
    }

    override fun onStart() {
        super.onStart()

        appComponent.appService.retrieveAppConfig(appComponent.signalHandler.currentUserId ?: Constants.EMPTY_USER_ID)
                .toObservable()
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple { appConfig ->
                    Glide.with(this)
                            .load(Uri.parse("http://qr.liantu.com/api.php?&bg=ffffff&fg=000000")
                                    .buildUpon()
                                    .appendQueryParameter("w", barcodeView.layoutParams.width.toString())
                                    .appendQueryParameter("text", appConfig.downloadUrl)
                                    .build())
                            .into(barcodeView)

                    shareButton.setOnClickListener {
                        startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, R.string.share_text.toFormattedString(this, appConfig.downloadUrl)),
                                R.string.share_via.toFormattedString(this)
                        ))
                    }
                }
    }
}