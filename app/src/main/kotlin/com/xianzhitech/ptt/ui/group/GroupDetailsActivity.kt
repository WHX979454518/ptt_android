package com.xianzhitech.ptt.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView
import com.crashlytics.android.answers.Answers
import com.crashlytics.android.answers.ContentViewEvent
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.StaticUserException
import com.xianzhitech.ptt.ui.base.BaseActivity
import com.xianzhitech.ptt.ui.base.FragmentDisplayActivity
import com.xianzhitech.ptt.ui.user.UserListAdapter
import com.xianzhitech.ptt.util.withUser
import rx.Subscriber

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
            joinRoom(CreateRoomRequest(groupIds = listOf(groupId)))
        }

        findViewById(R.id.groupDetails_videoChat).setOnClickListener {
            joinRoom(CreateRoomRequest(groupIds = listOf(groupId)), true)
        }

        allMemberLabel.setOnClickListener {
            val fragmentArgs = Bundle(1).apply { putString(GroupMemberListFragment.ARG_GROUP_ID, groupId) }
            startActivityWithAnimation(FragmentDisplayActivity.createIntent(GroupMemberListFragment::class.java, fragmentArgs))
        }
    }

    override fun onStart() {
        super.onStart()

        Answers.getInstance().logContentView(ContentViewEvent().apply {
            withUser(appComponent.signalHandler.peekCurrentUserId, appComponent.signalHandler.currentUserCache.value)
            putContentType("group-details")
            putContentId(groupId)
        })

        val appComponent = application as AppComponent
        appComponent.groupRepository.getGroups(listOf(groupId))
                .observe()
                .switchMap { groups ->
                    val group = groups.firstOrNull() ?: throw StaticUserException(R.string.error_group_not_exists)
                    appComponent.userRepository.getUsers(group.memberIds).observe()
                            .map { group to it }
                }
                .observeOnMainThread()
                .subscribe(object : Subscriber<Pair<Group, List<User>>>() {
                    override fun onError(e: Throwable) {
                        defaultOnErrorAction.call(e)
                        finish()
                    }

                    override fun onCompleted() { }

                    override fun onNext(t: Pair<Group, List<User>>) {
                        onGroupLoaded(t.first, t.second)
                    }
                })
                .bindToLifecycle()
    }

    private fun onGroupLoaded(group: Group, groupMembers: List<User>) {
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