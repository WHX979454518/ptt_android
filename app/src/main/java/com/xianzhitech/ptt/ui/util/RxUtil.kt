package com.xianzhitech.ptt.ui.util

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

import rx.Observable
import rx.subscriptions.Subscriptions

object RxUtil {

    fun fromTextChanged(editText: EditText): Observable<String> {
        return Observable.create<String> { subscriber ->
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable) {
                    subscriber.onNext(s.toString())
                }
            }
            editText.addTextChangedListener(watcher)
            subscriber.add(Subscriptions.create { editText.removeTextChangedListener(watcher) })
        }
    }

}
