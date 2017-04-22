package com.xianzhitech.ptt.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.data.ContactGroup
import com.xianzhitech.ptt.data.User
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.logErrorAndForget
import com.xianzhitech.ptt.ext.startActivityWithAnimation
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.service.toast
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.user.UserListAdapter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers

class GroupDetailsActivity : BaseActivity() {

    private lateinit var allMemberLabel: TextView
    private lateinit var groupNameView: TextView

    private val adapter = UserListAdapter(R.layout.view_room_member_list_item)
    private val groupId: String
        get() = intent.getStringExtra(EXTRA_GROUP_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_group_details)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val memberView = findView<RecyclerView>(R.id.groupDetails_members)
        memberView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.horizontal_member_item_count))
        memberView.adapter = adapter

        allMemberLabel = findView(R.id.groupDetails_allMemberLabel)
        groupNameView = findView(R.id.groupDetails_name)
        val callButton: View = findView(R.id.groupDetails_call)

        callButton.setOnClickListener {
            joinRoom(groupIds = listOf(groupId))
        }

        findViewById(R.id.groupDetails_videoChat).setOnClickListener {
            joinRoom(groupIds = listOf(groupId), isVideoChat = true)
        }

        allMemberLabel.setOnClickListener {
            val fragmentArgs = Bundle(1).apply { putString(GroupMemberListFragment.ARG_GROUP_ID, groupId) }
            startActivityWithAnimation(FragmentDisplayActivity.createIntent(GroupMemberListFragment::class.java, fragmentArgs))
        }
    }

    override fun onStart() {
        super.onStart()

        val appComponent = application as AppComponent

        appComponent.storage.getGroup(groupId)
                .switchMap { group ->
                    if (group.isPresent) {
                        appComponent.storage.getUsers(group.get().memberIds)
                                .map { members ->
                                    group.get() to members
                                }
                    } else {
                        Observable.empty()
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .logErrorAndForget {
                    it.toast()
                    finish()
                }
                .subscribe { (group, groupMembers) -> onGroupLoaded(group, groupMembers) }
                .bindToLifecycle()
    }

    private fun onGroupLoaded(group: ContactGroup, groupMembers: List<User>) {
        adapter.setUsers(groupMembers.subList(0, Math.min(groupMembers.size, MAX_MEMBER_DISPLAY_SIZE)))
        groupNameView.text = group.name
        allMemberLabel.text = R.string.all_member_with_number.toFormattedString(this, groupMembers.size)
    }

    companion object {
        const val EXTRA_GROUP_ID = "egi"
        const val MAX_MEMBER_DISPLAY_SIZE = 15

        fun build(context: Context, groupId: String): Intent {
            return Intent(context, GroupDetailsActivity::class.java).putExtra(EXTRA_GROUP_ID, groupId)
        }
    }
}