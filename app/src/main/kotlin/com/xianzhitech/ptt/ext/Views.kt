package com.xianzhitech.ptt.ext

import android.app.Activity
import android.databinding.BindingAdapter
import android.databinding.InverseBindingAdapter
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText

fun EditText.isEmpty() = text.isEmpty()

fun ViewGroup.inflate(@LayoutRes layout: Int, attachToRoot: Boolean = false) : View =
        LayoutInflater.from(context).inflate(layout, this, attachToRoot)

inline fun <reified T : View> Activity.findView(@IdRes id: Int) = findViewById(id) as T
inline fun <reified T : View> View.findView(@IdRes id: Int) = findViewById(id) as T

fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

@set:BindingAdapter("show")
var View.show : Boolean
    set(value) = if (value) visibility = View.VISIBLE else visibility = View.GONE
    get() = visibility == View.VISIBLE

@set:BindingAdapter("string")
@get:InverseBindingAdapter(attribute = "string")
var EditText.string : String
    set(value) {
        setText(value)
    }
    get() = text.toString()
