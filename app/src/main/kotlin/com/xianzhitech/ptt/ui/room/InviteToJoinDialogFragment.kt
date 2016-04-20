package com.xianzhitech.ptt.ui.room

import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AppCompatDialogFragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.ui.base.BaseActivity
import java.io.Serializable

class InviteToJoinDialogFragment : AppCompatDialogFragment() {

    private var multiMode = false
    private val adapter = Adapter()
    private var bottomBar : View? = null

    private val invites = arrayListOf<BaseActivity.InviteToJoinInfo>()

    fun addInvite(invite: BaseActivity.InviteToJoinInfo) {
        if (invites.contains(invite).not()) {
            invites.add(invite)
            invites.sortBy { it.inviteTime }
            if (invites.size > 1) {
                multiMode = true
            }
            bottomBar?.visibility = if (multiMode) View.VISIBLE else View.GONE
            adapter.notifyDataSetChanged()
        }
    }

    private fun addInvites(newInvites: Collection<BaseActivity.InviteToJoinInfo>) {
        invites.addAll(newInvites)
        invites.sortBy { it.inviteTime }
        if (invites.size > 1) {
            multiMode = true
        }
        bottomBar?.visibility = if (multiMode) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_fragment_invite, container).apply {
            val recyclerView = findView<RecyclerView>(R.id.inviteDialog_recyclerView)
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter

            bottomBar = findView<View>(R.id.inviteDialog_bottomBar).apply {
                findViewById(R.id.inviteDialog_ignoreAll).setOnClickListener {
                    cancelJoinRoom()
                }
            }
        }
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addInvites((savedInstanceState?.getSerializable(STATE_INVITES) ?: arguments.getSerializable(ARG_INITIAL_INVITES)) as List<BaseActivity.InviteToJoinInfo>)
    }

    internal fun joinRoom(invite: BaseActivity.InviteToJoinInfo) {
        callbacks<Callbacks>()?.joinRoomFromInvite(invite.roomId)
        dismissImmediately()
    }

    internal fun cancelJoinRoom() {
        dismissImmediately()
    }

    override fun onDestroyView() {
        bottomBar = null
        super.onDestroyView()
    }

    interface Callbacks {
        fun joinRoomFromInvite(roomId : String)
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.invite = invites[position]
        }

        override fun getItemViewType(position: Int): Int {
            return if (multiMode) VIEW_TYPE_MULTIPLE else VIEW_TYPE_SINGLE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder? {
            val layout = when (viewType) {
                VIEW_TYPE_SINGLE -> R.layout.dialog_fragment_invite_single_item
                else -> R.layout.dialog_fragment_invite_multi_item
            }

            return ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        }

        override fun getItemCount() = invites.size
    }

    private inner class ViewHolder(rootView : View,
                                   val inviterIcon : ImageView = rootView.findView(R.id.inviteItem_inviterIcon),
                                   val inviterName : TextView = rootView.findView(R.id.inviteItem_inviterName),
                                   val joinButton : View = rootView.findViewById(R.id.inviteItem_join),
                                   val inviteTime : TextView? = rootView.findView(R.id.inviteItem_time),
                                   val cancelButton : View? = rootView.findViewById(R.id.inviteItem_cancel)) : RecyclerView.ViewHolder(rootView) {
        var invite : BaseActivity.InviteToJoinInfo? = null
        set(value) {
            if (field != value) {
                field = value
                inviterIcon.setImageDrawable(value?.inviter?.createAvatarDrawable(this@InviteToJoinDialogFragment))
                inviterName.text = value?.inviter?.name
                inviteTime?.text = value?.inviteTime?.formatInvite(itemView.context)
            }
        }

        init {
            joinButton.setOnClickListener {
                invite?.let { joinRoom(it) }
            }

            cancelButton?.setOnClickListener {
                invite?.let { cancelJoinRoom() }
            }
        }
    }

    class Builder {
        var invites : List<BaseActivity.InviteToJoinInfo> = arrayListOf()

        fun showImmediately(manager: FragmentManager, tag: String) : InviteToJoinDialogFragment {
            if (invites.isEmpty()) {
                throw IllegalStateException("Has to have more than one invite")
            }

            val fragment = InviteToJoinDialogFragment().apply {
                arguments = Bundle(1).apply {
                    putSerializable(ARG_INITIAL_INVITES, invites as Serializable)
                }
            }

            fragment.show(manager, tag)
            manager.executePendingTransactions()
            return fragment
        }
    }

    companion object {
        private const val VIEW_TYPE_SINGLE = 0
        private const val VIEW_TYPE_MULTIPLE = 1

        const val ARG_INITIAL_INVITES = "arg_ii"
        private const val STATE_INVITES = "state_invites"
    }
}