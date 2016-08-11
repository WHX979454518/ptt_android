package com.xianzhitech.ptt.ui.group

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.model.Model
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.ui.home.ModelProvider
import rx.Observable


class GroupMemberProvider(private val groupId: String) : ModelProvider, Parcelable {
    override fun getModels(context: Context): Observable<List<Model>> {
        val appComponent = context.applicationContext as AppComponent
        return appComponent.groupRepository.getGroups(listOf(groupId)).observe()
                .switchMap {
                    val group = it.firstOrNull() ?: throw StaticUserException(R.string.error_group_not_exists)
                    appComponent.userRepository.getUsers(group.memberIds).observe()
                }
    }

    override val selectable: Boolean
        get() = false
    override val preselectedModelIds: Collection<String>
        get() = emptyList()
    override val preselectedUnselectable: Boolean
        get() = false

    constructor(source: Parcel) : this(source.readString())

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(groupId)
    }

    companion object {
        @JvmField val CREATOR: Parcelable.Creator<GroupMemberProvider> = object : Parcelable.Creator<GroupMemberProvider> {
            override fun createFromParcel(source: Parcel): GroupMemberProvider {
                return GroupMemberProvider(source)
            }

            override fun newArray(size: Int): Array<GroupMemberProvider?> {
                return arrayOfNulls(size)
            }
        }
    }
}