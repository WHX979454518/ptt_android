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
import com.xianzhitech.ptt.data.RoomWithMembersAndName
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.appComponent
import com.xianzhitech.ptt.ext.atMost
import com.xianzhitech.ptt.ui.widget.drawable.MultiDrawable
import com.xianzhitech.ptt.ui.widget.drawable.TextDrawable
import com.xianzhitech.ptt.ui.widget.drawable.UriDrawable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable


/**
 * 显示一个用户、组或者会话的图标
 */
open class ModelView @JvmOverloads constructor(context: Context,
                                          attributeSet: AttributeSet? = null,
                                          defStyle: Int = 0) : AppCompatImageView(context, attributeSet, defStyle) {

    private var disposable: Disposable? = null
    private var isAttached: Boolean = false

    var model: Any? = null
        set(value) {
            if (field != value) {
                field = value
                disposable?.dispose()
                subscribeIfNeeded()
            }
        }

    private fun subscribeIfNeeded() {
        val model = this.model
        if (isAttached && model != null) {
            val users: Single<out List<User>>

            when (model) {
                is User -> {
                    setImageDrawable(createUserDrawable(model))
                    return
                }
                is ContactGroup -> {
                    if (model.avatar != null) {
                        setImageDrawable(UriDrawable(Glide.with(context), Uri.parse(model.avatar)))
                        return
                    }

                    setImageDrawable(null)
                    users = context.appComponent.storage.getUsers(model.memberIds.toList().atMost(9))
                            .observeOn(AndroidSchedulers.mainThread())
                            .firstOrError()
                }
                is RoomWithMembersAndName -> {
                    users = Single.just(model.members)
                }
                is Room -> {
                    setImageDrawable(null)
                    users = context.appComponent.storage.getRoomMembers(model, 9, includeSelf = false)
                            .observeOn(AndroidSchedulers.mainThread())
                            .firstOrError()
                }
                else -> throw IllegalStateException("Unknown model type $model")
            }

            disposable = users

                    .subscribe { users ->
                        val drawable = when (users.size) {
                            0 -> null
                            1 -> createUserDrawable(users.first())
                            else -> MultiDrawable(context, children = users.map(this::createUserDrawable))
                        }

                        setImageDrawable(drawable)
                    }
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