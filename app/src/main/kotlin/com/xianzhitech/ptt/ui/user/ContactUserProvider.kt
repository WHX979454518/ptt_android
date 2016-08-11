package com.xianzhitech.ptt.ui.user

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.ui.home.BaseModelProvider
import rx.Observable

class ContactUserProvider : BaseModelProvider {
    constructor(selectable : Boolean = false,
                preselectedUserIds : Collection<String> = emptyList(),
                preselectedUnselectable : Boolean = false) : super(selectable, preselectedUserIds, preselectedUnselectable)
    private constructor(source : Parcel) : super(source)

    override fun getModels(context: Context): Observable<List<Model>> {
        return (context.applicationContext as AppComponent).contactRepository.getAllContactUsers().observe() as Observable<List<Model>>
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<ContactUserProvider> = object : Parcelable.Creator<ContactUserProvider> {
            override fun createFromParcel(source: Parcel): ContactUserProvider {
                return ContactUserProvider(source)
            }

            override fun newArray(size: Int): Array<ContactUserProvider?> {
                return arrayOfNulls(size)
            }
        }
    }
}