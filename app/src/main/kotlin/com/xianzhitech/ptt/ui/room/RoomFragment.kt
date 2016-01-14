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
import com.xianzhitech.ptt.ext.ensureArguments
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getIcon
import com.xianzhitech.ptt.ext.getTintedDrawable
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.service.provider.JoinRoomRequest
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.home.AlertDialogFragment
import com.xianzhitech.ptt.ui.widget.PushToTalkButton
import java.util.*
import kotlin.collections.arrayListOf
import kotlin.collections.emptyList
import kotlin.collections.hashSetOf
import kotlin.collections.sortWith

class RoomFragment : BaseFragment<RoomFragment.Callbacks>()
        , PushToTalkButton.Callbacks
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
    private lateinit var roomRepository: RoomRepository

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

    private var roomRequest: JoinRoomRequest
        set(value) = ensureArguments().putSerializable(ARG_ROOM_REQUEST, value)
        get() = ensureArguments().getSerializable(ARG_ROOM_REQUEST) as JoinRoomRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //        speakSourceAdapter.speakerModes = SpeakerMode.values().toList()
        roomRepository = (activity.application as AppComponent).roomRepository
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
    }

    override fun onStop() {

        super.onStop()
    }

    override fun onBackPressed(): Boolean {
        return true
    }

    override fun requestMic() {

    }

    override fun releaseMic() {
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) {
    }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) {
    }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) {
    }

    private class Adapter : RecyclerView.Adapter<ViewHolder>(), Comparator<User> {
        val activeMembers = hashSetOf<String>()
        val members = arrayListOf<User>()
        var currentSpeakerId: String? = null
            set(newSpeaker) {
                if (field != newSpeaker) {
                    field = newSpeaker
                    notifyDataSetChanged()
                }
            }

        fun setMembers(newMembers: Collection<User>, newActiveMembers: Collection<String>) {
            members.clear()
            members.addAll(newMembers)
            members.sortWith(this)
            activeMembers.clear()
            activeMembers.addAll(newActiveMembers)
            notifyDataSetChanged()
        }

        override fun compare(lhs: User, rhs: User): Int {
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

        fun create(request: JoinRoomRequest): Fragment {
            val fragment = RoomFragment()
            val args = Bundle(1)
            args.putSerializable(ARG_ROOM_REQUEST, request)
            fragment.arguments = args
            return fragment
        }
    }
}