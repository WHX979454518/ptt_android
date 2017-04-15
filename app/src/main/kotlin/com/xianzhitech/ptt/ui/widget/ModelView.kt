package com.xianzhitech.ptt.ui.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import com.bumptech.glide.Glide
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.ContactGroup
import com.xianzhitech.ptt.data.Room
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.atMost
import com.xianzhitech.ptt.ui.widget.drawable.MultiDrawable
import com.xianzhitech.ptt.ui.widget.drawable.TextDrawable
import com.xianzhitech.ptt.ui.widget.drawable.UriDrawable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable


/**
 * 显示一个用户、组或者会话的图标
 */
class ModelView @JvmOverloads constructor(context: Context,
                                          attributeSet: AttributeSet? = null,
                                          defStyle: Int = 0) : AppCompatImageView(context, attributeSet, defStyle) {

    private var disposable: Disposable? = null
    private var isAttached: Boolean = false

    var model: Any? = null
        set(value) {
            if (field != value) {
                field = value
                disposable?.dispose()
                setImageDrawable(null)
                subscribeIfNeeded()
            }
        }

    private fun subscribeIfNeeded() {
        val model = this.model
        if (isAttached && model != null) {
            val drawable: Single<Drawable>

            when (model) {
                is User -> {
                    setImageDrawable(createUserDrawable(model))
                    return
                }
                is ContactGroup -> {
                    drawable = context.appComponent.storage.getUsers(model.memberIds.toList().atMost(9))
                            .firstOrError()
                            .map { MultiDrawable(context, it.map(this::createUserDrawable)) }
                }
                is Room -> {
                    drawable = context.appComponent.storage.getRoomMembers(model, 9)
                            .firstOrError()
                            .map { MultiDrawable(context, it.map(this::createUserDrawable)) }
                }
                else -> throw IllegalStateException("Unknown model type $model")
            }

            disposable = drawable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::setImageDrawable)
        }
    }

    private fun createUserDrawable(model: User): Drawable {
        return if (model.avatar.isNullOrEmpty()) {
            TextDrawable(model.name[0].toString(), resources.getIntArray(R.array.account_colors).let {
                it[Math.abs(model.name.hashCode()) % it.size]
            })
        } else {
            UriDrawable(Glide.with(context), Uri.parse(model.avatar))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true

        subscribeIfNeeded()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAttached = false

        disposable?.dispose()
        disposable = null
    }
}