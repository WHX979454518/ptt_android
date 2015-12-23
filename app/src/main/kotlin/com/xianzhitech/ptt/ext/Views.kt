package com.xianzhitech.ptt.ext

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import rx.Observable
import rx.subscriptions.Subscriptions


fun EditText.getString() = text.toString()
fun EditText.fromTextChanged() = Observable.create<String> {
    val watcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            it.onNext(getString())
        }
    }
    addTextChangedListener(watcher)

    it.add(Subscriptions.create { removeTextChangedListener(watcher) })
}