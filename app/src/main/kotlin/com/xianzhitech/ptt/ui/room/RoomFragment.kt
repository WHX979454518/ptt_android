package com.xianzhitech.ptt.ui.room

import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.view.ViewCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Conversation
import com.xianzhitech.ptt.model.Person
import com.xianzhitech.ptt.presenter.RoomPresenter
import com.xianzhitech.ptt.presenter.RoomPresenterView
import com.xianzhitech.ptt.repo.ConversationRepository
import com.xianzhitech.ptt.repo.ConversationWithMemberNames
import com.xianzhitech.ptt.service.provider.ConversationRequest
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.home.AlertDialogFragment
import com.xianzhitech.ptt.ui.widget.PushToTalkButton
import java.util.*
import kotlin.collections.arrayListOf
import kotlin.collections.emptyList
import kotlin.collections.hashSetOf
import kotlin.collections.sortWith
import kotlin.text.isNullOrBlank

class RoomFragment : BaseFragment<RoomFragment.Callbacks>()
        , PushToTalkButton.Callbacks
        , RoomPresenterView
        , BackPressable
        , AlertDialogFragment.OnPositiveButtonClickListener
        , AlertDialogFragment.OnNegativeButtonClickListener
        , AlertDialogFragment.OnNeutralButtonClickListener {

    private class Views(rootView: View,
                        val toolbar: Toolbar = rootView.findView(R.id.room_toolbar),
                        val pttBtn: PushToTalkButton = rootView.findView(R.id.room_pushToTalkButton),
                        val appBar: ViewGroup = rootView.findView(R.id.room_appBar),
                        val speakerSourceView: Spinner = rootView.findView(R.id.room_speakerSource),
                        val progressBar: View = rootView.findView(R.id.room_progress),
                        val roomStatusView: TextView = rootView.findView(R.id.room_status),
                        val memberView: RecyclerView = rootView.findView(R.id.room_memberList))

    private enum class SpeakerMode(val titleResId: Int) {
        SPEAKER(R.string.source_speaker),
        HEADPHONE(R.string.source_headphone),
        BLUETOOTH(R.string.source_bluetooth)
    }

    private var views: Views? = null
    private val adapter = Adapter()
    private var presenter: RoomPresenter? = null
    private var backgroundRoomPresenterView : RoomPresenterView ? = null
    private lateinit var conversationRepository: ConversationRepository

    private val speakSourceAdapter = object : BaseAdapter() {
        var speakerModes: List<SpeakerMode> = emptyList()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
            val view = (convertView as? TextView) ?: TextView(parent!!.context)
            view.setText(speakerModes[position].titleResId)
            return view
        }

        override fun getItem(position: Int) = speakerModes[position]
        override fun getItemId(position: Int) = speakerModes[position].ordinal.toLong()
        override fun getCount() = speakerModes.size
    }

    private var roomRequest: ConversationRequest
        set(value) = ensureArguments().putSerializable(ARG_ROOM_REQUEST, value)
        get() = ensureArguments().getSerializable(ARG_ROOM_REQUEST) as ConversationRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //        speakSourceAdapter.speakerModes = SpeakerMode.values().toList()
        conversationRepository = (activity.application as AppComponent).conversationRepository
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_room, container, false)?.apply {
            views = Views(this).apply {
                memberView.layoutManager = GridLayoutManager(context, 7)
                memberView.adapter = adapter

                toolbar.navigationIcon = context.getTintedDrawable(R.drawable.ic_arrow_back, Color.WHITE)
                toolbar.setNavigationOnClickListener { v -> activity.finish() }
                ViewCompat.setElevation(pttBtn, ViewCompat.getElevation(appBar) + resources.getDimension(R.dimen.divider_normal))
                pttBtn.callbacks = this@RoomFragment

                speakerSourceView.adapter = speakSourceAdapter
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val appComponent = context.applicationContext as AppComponent
        backgroundRoomPresenterView = appComponent.backgroundRoomPresenterView

        presenter = appComponent.roomPresenter.apply {
            attachView(this@RoomFragment)
            detachView(appComponent.backgroundRoomPresenterView)
            requestJoinRoom(roomRequest, false)
        }

    }

    override fun onStop() {
        presenter?.apply {
            backgroundRoomPresenterView?.let { attachView(it) }
            detachView(this@RoomFragment)
        }


        super.onStop()
    }

    override fun onBackPressed(): Boolean {
        presenter?.requestQuitCurrentRoom()
        return true
    }

    override fun promptCurrentJoinedRoomIsImportant(currentRoom: Conversation) {
        AlertDialogFragment.Builder()
                .setTitle(R.string.room_prompt_important.toFormattedString(context))
                .setMessage(R.string.room_prompt_important_message.toFormattedString(context, currentRoom.name))
                .setBtnNeutral(R.string.dialog_ok.toFormattedString(context))
                .show(childFragmentManager, TAG_PROMPT_IMPORTANT)
    }

    override fun showRoom(room: Conversation) {
        views?.apply {
            if (room.name.isNullOrBlank()) {
                conversationRepository.getConversationWithMemberNames(room.id, 3)
                        .observeOnMainThread()
                        .compose(bindToLifecycle())
                        .subscribe(object : GlobalSubscriber<ConversationWithMemberNames?>() {
                            override fun onNext(t: ConversationWithMemberNames?) {
                                toolbar.title = t?.getMemberNames(activity)
                            }
                        })
            } else {
                toolbar.title = room.name
            }

            pttBtn.connectedToRoom = true
        }
    }

    override fun showCurrentSpeaker(speaker: Person?, isSelf: Boolean) {
        views?.apply {
            if (speaker == null) {
                roomStatusView.animate().alpha(0f).start()
            } else {
                roomStatusView.animate().alpha(1f).start()
                roomStatusView.text = if (isSelf) R.string.room_talking.toFormattedString(context)
                else R.string.room_other_is_talking.toFormattedString(context, speaker.name)
            }

            pttBtn.hasActiveSpeaker = speaker != null
            pttBtn.isEnabled = speaker == null
        }

        adapter.currentSpeakerId = speaker?.id
    }

    override fun showRoomMembers(members: List<Person>, activeMemberIds: Collection<String>) {
        adapter.setMembers(members, activeMemberIds)
    }

    override fun promptConfirmSwitchingRoom(newRoom: Conversation) {
        AlertDialogFragment.Builder()
                .setTitle(R.string.room_prompt_switching.toFormattedString(context))
                .setMessage(R.string.room_prompt_switching_message.toFormattedString(context, newRoom.name))
                .setBtnPositive(R.string.dialog_confirm.toFormattedString(context))
                .setBtnNegative(R.string.dialog_cancel.toFormattedString(context))
                .show(childFragmentManager, TAG_SWITCHING_ROOM_CONFIRM)
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
        if (fragment.tag == TAG_SWITCHING_ROOM_CONFIRM) {
            presenter?.requestJoinRoom(roomRequest, true)
        }

        fragment.dismiss()
    }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) {
        if (fragment.tag == TAG_SWITCHING_ROOM_CONFIRM) {
            callbacks?.onRoomQuited()
        }

        fragment.dismiss()
    }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) {
        fragment.dismiss()
    }

    override fun onRoomQuited(conversation: Conversation?) {
        callbacks?.onRoomQuited()
    }

    override fun onRoomJoined(conversationId: String) {
        //Do nothing
    }

    override fun showRequestingMic(isRequesting: Boolean) {
        views?.pttBtn?.isRequestingMic = isRequesting
    }

    override fun showLoading(visible: Boolean) {
        views?.progressBar?.setVisible(visible)
    }

    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }

    override fun requestMic() {
        presenter?.requestMic()
    }

    override fun releaseMic() {
        presenter?.releaseMic()
    }

    private class Adapter : RecyclerView.Adapter<ViewHolder>(), Comparator<Person> {
        val activeMembers = hashSetOf<String>()
        val members = arrayListOf<Person>()
        var currentSpeakerId: String? = null
            set(newSpeaker) {
                if (field != newSpeaker) {
                    field = newSpeaker
                    notifyDataSetChanged()
                }
            }

        fun setMembers(newMembers: Collection<Person>, newActiveMembers: Collection<String>) {
            members.clear()
            members.addAll(newMembers)
            members.sortWith(this)
            activeMembers.clear()
            activeMembers.addAll(newActiveMembers)
            notifyDataSetChanged()
        }

        override fun compare(lhs: Person, rhs: Person): Int {
            val lhsActive = activeMembers.contains(lhs.id)
            val rhsActive = activeMembers.contains(rhs.id)
            if (lhsActive && rhsActive || (!lhsActive && !rhsActive)) {
                return lhs.name.compareTo(rhs.name)
            } else if (lhsActive) {
                return 1
            } else {
                return -1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = ViewHolder(parent!!)

        override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
            holder?.imageView?.let {
                val person = members[position]
                it.setImageDrawable(person.getIcon(it.context))
                it.isEnabled = activeMembers.contains(person.id)
                it.isSelected = currentSpeakerId == person.id
            }
        }

        override fun getItemCount() = members.size
    }

    private class ViewHolder(container: ViewGroup,
                             val imageView: ImageView = LayoutInflater.from(container.context).inflate(R.layout.view_room_member_item, container, false) as ImageView)
    : RecyclerView.ViewHolder(imageView)

    interface Callbacks {
        fun onRoomQuited()
    }

    companion object {
        const val ARG_ROOM_REQUEST = "arg_room_request"

        private const val TAG_SWITCHING_ROOM_CONFIRM = "tag_switching_room_confirm"
        private const val TAG_PROMPT_IMPORTANT = "tag_prompt_important"

        fun create(request: ConversationRequest): Fragment {
            val fragment = RoomFragment()
            val args = Bundle(1)
            args.putSerializable(ARG_ROOM_REQUEST, request)
            fragment.arguments = args
            return fragment
        }
    }
}