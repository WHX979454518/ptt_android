package com.xianzhitech.ptt.ext

import android.app.Activity
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

fun EditText.isEmpty() = text.isEmpty()

fun ViewGroup.inflate(@LayoutRes layout : Int, attachToRoot : Boolean = false) =
    LayoutInflater.from(context).inflate(layout, this, attachToRoot)

inline fun <reified T : View> Activity.findView(@IdRes id: Int) = findViewById(id) as T
inline fun <reified T : View> View.findView(@IdRes id: Int) = findViewById(id) as T
fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}