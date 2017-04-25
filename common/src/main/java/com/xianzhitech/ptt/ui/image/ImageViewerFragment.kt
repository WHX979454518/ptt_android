package com.xianzhitech.ptt.ui.image

import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.piasy.biv.indicator.progresspie.ProgressPieIndicator
import com.github.piasy.biv.view.BigImageView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ui.base.BaseFragment


class ImageViewerFragment : BaseFragment() {
    private lateinit var imageView : BigImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Base_Theme_AppCompat_Dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        imageView = BigImageView(context)
        imageView.setProgressIndicator(ProgressPieIndicator())
        imageView.showImage(Uri.parse(arguments.getString(ARG_URI)))
        return imageView
    }

    companion object {
        const val ARG_URI = "uri"

        fun createInstance(uri : String) : ImageViewerFragment {
            return ImageViewerFragment().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_URI, uri)
                }
            }
        }
    }
}