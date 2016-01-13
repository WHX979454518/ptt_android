package com.xianzhitech.ptt.presenter

import com.xianzhitech.ptt.model.ContactItem
import com.xianzhitech.ptt.presenter.base.PresenterView

interface ContactListPresenterView : PresenterView {
    fun showContactList(contactList: List<ContactItem>)
}