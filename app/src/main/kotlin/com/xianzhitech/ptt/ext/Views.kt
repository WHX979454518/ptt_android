package com.xianzhitech.ptt.ext

import android.app.Activity
import android.graphics.drawable.Drawable
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.text.Editable
import android.text.TextWatcher
import android.util.Property
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
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

fun ViewGroup.inflate(@LayoutRes layout: Int, attachToRoot: Boolean = false) =
        LayoutInflater.from(context).inflate(layout, this, attachToRoot)

inline fun <reified T : View> Activity.findView(@IdRes id: Int) = findViewById(id) as T
inline fun <reified T : View> View.findView(@IdRes id: Int) = findViewById(id) as T
fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

// Properties
val TRANSLATION_Y_FRACTION = object : Property<View, Float>(Float::class.java, "translationFractionY") {
    override fun get(view: View?) = view?.let {
        it.translationY / it.height
    } ?: 0f

    override fun set(view: View, value: Float) {
        view.translationY = value * view.height
    }
}

val TextView.compoundDrawableLeft: Drawable?
    get() = compoundDrawables[0]

val TextView.compoundDrawableTop: Drawable?
    get() = compoundDrawables[1]

val TextView.compoundDrawableRight: Drawable?
    get() = compoundDrawables[2]

val TextView.compoundDrawableBottom: Drawable?
    get() = compoundDrawables[3]
