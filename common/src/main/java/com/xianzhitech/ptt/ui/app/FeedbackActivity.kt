package com.xianzhitech.ptt.ui.app

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.api.dto.Feedback
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.isEmpty
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import io.reactivex.CompletableObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable


class FeedbackActivity : BaseActivity(), AlertDialogFragment.OnPositiveButtonClickListener {
    private lateinit var titleView : EditText
    private lateinit var messageView : EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_feedback)
        titleView = findView(R.id.feedback_title)
        messageView = findView(R.id.feedback_content)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        messageView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                supportInvalidateOptionsMenu()
            }
        })
    }

    override fun onBackPressed() {
        if (titleView.isEmpty().not() || messageView.isEmpty().not()) {
            AlertDialogFragment.Builder().apply {
                message = R.string.error_has_unsaved_change.toFormattedString(this@FeedbackActivity)
                btnPositive = R.string.give_up_and_exit.toFormattedString(this@FeedbackActivity)
                btnNegative = R.string.dialog_cancel.toFormattedString(this@FeedbackActivity)
                cancellabe = true
            }.show(supportFragmentManager, TAG_CONFIRMATION)
        } else {
            super.onBackPressed()
        }
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        if (fragment.tag == TAG_CONFIRMATION) {
            super.onBackPressed()
            return
        }

        super.onPositiveButtonClicked(fragment)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.feedback, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.feedback_done) {
            if (messageView.text.toString().isNullOrBlank()) {
                messageView.error = R.string.error_empty_content.toFormattedString(this)
                return false
            }

            Toast.makeText(this, R.string.thanks_for_feedback, Toast.LENGTH_LONG).show()

            appComponent.appApi.submitFeedback(Feedback(
                    title = titleView.text.toString(),
                    message = messageView.text.toString(),
                    userId = appComponent.signalBroker.peekUserId()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(FeedbackSubscriber(applicationContext))

            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private class FeedbackSubscriber(private val appContext : Context) : CompletableObserver {
        override fun onComplete() {
            Toast.makeText(appContext, R.string.feedback_complete, Toast.LENGTH_LONG).show()
        }

        override fun onSubscribe(d: Disposable?) {
        }

        override fun onError(e: Throwable?) {
            e.toast()
        }
    }

    companion object {
        private const val TAG_CONFIRMATION = "tag_quit_confirmation"
    }
}

