package com.xianzhitech.ptt.ui.home

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.xianzhitech.ptt.model.NamedModel
import rx.Observable

interface ModelProvider : Parcelable {
    fun getModels(context: Context) : Observable<List<NamedModel>>
    val selectable : Boolean
    val preselectedModelIds : Collection<String>
    val preselectedUnselectable : Boolean
}

abstract class BaseModelProvider(override val selectable : Boolean,
                                 override val preselectedModelIds : Collection<String>,
                                 override val preselectedUnselectable : Boolean) : ModelProvider {


    protected constructor(source: Parcel) : this(source.readByte().let { it.toInt() != 0 }, source.createStringArray().toList(), source.readByte().let { it.toInt() != 0 })

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte(if (selectable) 1 else 0)
        dest.writeStringArray(preselectedModelIds.toTypedArray())
        dest.writeByte(if (preselectedUnselectable) 1 else 0)
    }

}