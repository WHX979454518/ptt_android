package com.xianzhitech.ptt.ui.widget

import android.content.Context
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.ext.createAvatarDrawable
import com.xianzhitech.ptt.ext.mapFirst
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import rx.Subscriber
import java.lang.ref.WeakReference

class ModelDrawable(private val context: Context,
                    private val model : Model,
                    private val maxMemberDisplayCount : Int = Constants.MAX_MEMBER_DISPLAY_COUNT) : DrawableWrapper() {

    init {
        val appComponent = (context.applicationContext as AppComponent)

        when (model) {
            is User -> drawable = model.createAvatarDrawable(context)
            is Group -> {
                appComponent.userRepository.getUsers(model.memberIds.mapFirst(maxMemberDisplayCount, { it }))
                        .observe()
                        .observeOnMainThread()
                        .subscribe(WeakUserListSubscriber(this, false))
            }
            is Room -> {
                appComponent.roomRepository.getRoomMembers(model.id, maxMemberDisplayCount)
                        .observe()
                        .observeOnMainThread()
                        .subscribe(WeakUserListSubscriber(this, true))
            }
        }
    }

    private class WeakUserListSubscriber(modelDrawable: ModelDrawable,
                                         private val excludeCurrentUser : Boolean) : Subscriber<List<User>>() {
        private val modelDrawableRef = WeakReference(modelDrawable)

        override fun onError(e: Throwable?) { }
        override fun onCompleted() { }
        override fun onNext(t: List<User>) {
            modelDrawableRef.get()?.apply {
                val currentUserId = (context.applicationContext as AppComponent).signalService.peekLoginState().currentUserID
                if (t.size <= 2 && currentUserId != null && excludeCurrentUser) {
                    drawable = t.firstOrNull { it.id != currentUserId }?.createAvatarDrawable(context)
                }
                else {
                    drawable = MultiDrawable(context, t.map { it.createAvatarDrawable(context) })
                }
            } ?: unsubscribe()
        }
    }
}

fun Model.createDrawable(context: Context, maxMemberDisplayCount: Int = Constants.MAX_MEMBER_DISPLAY_COUNT) : ModelDrawable {
    return ModelDrawable(context, this, maxMemberDisplayCount)
}