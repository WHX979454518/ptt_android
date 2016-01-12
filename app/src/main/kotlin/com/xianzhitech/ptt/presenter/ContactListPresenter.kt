package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.presenter.base.BasePresenter
import com.xianzhitech.ptt.repo.ContactRepository
import rx.Subscription
import rx.subjects.BehaviorSubject

/**
 * Created by fanchao on 9/01/16.
 */
class ContactListPresenter(private val contactRepository: ContactRepository) : BasePresenter<ContactListPresenterView>() {
    private var contactListSubject = BehaviorSubject.create<List<ContactItem>>()
    private var subscription: Subscription? = null

    override fun attachView(view: ContactListPresenterView) {
        super.attachView(view)

        if (subscription == null) {
            subscription = contactRepository.getContactItems()
                    .observeOnMainThread()
                    .subscribe(object : GlobalSubscriber<List<ContactItem>>() {
                        override fun onError(e: Throwable) {
                            notifyViewsError(e)
                        }

                        override fun onNext(t: List<ContactItem>) {
                            contactListSubject.onNext(t)
                            views.forEach { it.showContactList(t) }
                        }
                    })
        }

        contactListSubject.value?.let { view.showContactList(it) }
    }

    override fun detachView(view: ContactListPresenterView) {
        super.detachView(view)

        if (views.isEmpty()) {
            subscription = subscription?.let { it.unsubscribe(); null }
        }
    }

    fun search(term: String?) {
        subscription?.unsubscribe()

        subscription = (if (term.isNullOrEmpty()) contactRepository.getContactItems() else contactRepository.searchContactItems(term!!))
                .observeOnMainThread()
                .subscribe(object : GlobalSubscriber<List<ContactItem>>() {
                    override fun onError(e: Throwable) {
                        notifyViewsError(e)
                    }

                    override fun onNext(t: List<ContactItem>) {
                        contactListSubject.onNext(t)
                        views.forEach { it.showContactList(t) }
                    }
                })
    }
}

