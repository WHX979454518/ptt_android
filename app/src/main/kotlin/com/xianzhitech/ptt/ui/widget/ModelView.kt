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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable


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
            val drawable: Observable<Drawable>

            when (model) {
                is User -> drawable = getUserDrawable(model)
                is ContactGroup -> {
                    drawable = context.appComponent.storage.getUsers(model.memberIds.toList().atMost(9))
                            .switchMap {
                                Observable.combineLatest(it.map(this::getUserDrawable)) {
                                    @Suppress("UNCHECKED_CAST")
                                    MultiDrawable(context, it.toList() as List<Drawable>)
                                }
                            }
                }
                is Room -> {
                    drawable = context.appComponent.storage.getRoomMembers(model, 9)
                            .switchMap {
                                Observable.combineLatest(it.map(this::getUserDrawable)) {
                                    @Suppress("UNCHECKED_CAST")
                                    MultiDrawable(context, it.toList() as List<Drawable>)
                                }
                            }
                }
                else -> throw IllegalStateException("Unknown model type $model")
            }

            disposable = drawable.subscribe(this::setImageDrawable)
        }
    }

    private fun getUserDrawable(model: User): Observable<Drawable> {
        return context.appComponent.storage.getUsers(listOf(model.id))
                .flatMap { it.firstOrNull()?.let { Observable.just(it as User) } ?: Observable.empty() }
                .observeOn(AndroidSchedulers.mainThread())
                .startWith(model)
                .distinctUntilChanged()
                .map {
                    if (model.avatar.isNullOrEmpty()) {
                        TextDrawable(model.name[0].toString(), resources.getIntArray(R.array.account_colors).let {
                            it[Math.abs(model.name.hashCode()) % it.size]
                        })
                    } else {
                        UriDrawable(Glide.with(context), Uri.parse(model.avatar))
                    }
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