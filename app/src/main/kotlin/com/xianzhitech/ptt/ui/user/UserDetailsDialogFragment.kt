package com.xianzhitech.ptt.ui.user

import android.animation.Animator
import android.animation.ObjectAnimator
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatDialog
import android.support.v7.app.AppCompatDialogFragment
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.TRANSLATION_Y_FRACTION
import com.xianzhitech.ptt.ext.createAvatarDrawable
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.ext.observeOnMainThread
import rx.Subscription

class UserDetailsDialogFragment : AppCompatDialogFragment() {
    companion object {
        const val ARG_USER_ID = "arg_user_id"

        fun build(userId : String) : UserDetailsDialogFragment {
            return UserDetailsDialogFragment().apply {
                arguments = Bundle(1).apply { putString(ARG_USER_ID, userId) }
            }
        }
    }

    private var views : Views? = null
    private var subscription : Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NORMAL, R.style.TransparentDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_user_details, container, false).apply {
            setOnClickListener { dismiss() }

            alpha = 0f
            animate().alpha(1f).start()

            views = Views(this).apply {
                toolbar.navigationIcon = context.getTintedDrawable(R.drawable.ic_arrow_back, Color.WHITE)
                toolbar.setNavigationOnClickListener { dismiss() }
                this.container.isClickable = true
                ObjectAnimator.ofFloat(this.container, TRANSLATION_Y_FRACTION, 1f, 0f).start()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : AppCompatDialog(getActivity(), getTheme()) {
            override fun onBackPressed() {
                this@UserDetailsDialogFragment.dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        subscription = (context.applicationContext as AppComponent).userRepository
            .getUser(arguments.getString(ARG_USER_ID)).observe()
            .observeOnMainThread()
            .subscribe {
                views?.iconView?.setImageDrawable(it?.createAvatarDrawable(this))
            }
    }

    override fun onStop() {
        subscription?.unsubscribe()
        subscription = null

        super.onStop()
    }

    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }

    override fun dismiss() {
        views?.apply {
            rootView.animate().alpha(0f).start()
            ObjectAnimator.ofFloat(container, TRANSLATION_Y_FRACTION, 1f).apply {
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationEnd(p0: Animator?) {
                        dismissAllowingStateLoss()
                    }

                    override fun onAnimationRepeat(p0: Animator?) { }
                    override fun onAnimationCancel(p0: Animator?) { }
                    override fun onAnimationStart(p0: Animator?) {}
                })

                start()
            }
        }
    }

    private class Views(val rootView: View,
                        val toolbar : Toolbar = rootView.findView(R.id.toolbar),
                        val container : View = rootView.findView(R.id.userDetails_container),
                        val iconView : ImageView = rootView.findView(R.id.userDetails_icon))
}