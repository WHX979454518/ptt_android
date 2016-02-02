package com.xianzhitech.ptt.ui.room

import android.graphics.Color
import android.os.Bundle
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
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.RoomWithMembers
import com.xianzhitech.ptt.repo.optRoomWithMembers
import com.xianzhitech.ptt.repo.optUser
import com.xianzhitech.ptt.service.BackgroundServiceBinder
import com.xianzhitech.ptt.service.LoginState
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.home.AlertDialogFragment
import com.xianzhitech.ptt.ui.widget.PushToTalkButton
import rx.Observable
import java.util.*

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
    private var backgroundServiceBinder : BackgroundServiceBinder? = null

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

                toolbar.inflateMenu(R.menu.room)
                toolbar.setOnMenuItemClickListener {
                    if (it.itemId == R.id.room_exit) {
                        backgroundServiceBinder?.requestQuitCurrentRoom()?.subscribe(GlobalSubscriber())
                        activity?.finish()
                    }

                    true
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val appComponent = context.applicationContext as AppComponent
        val bgService = appComponent
                .connectToBackgroundService()
                .observeOnMainThread()
                .publish()

        bgService.compose(bindToLifecycle()).subscribe(object : GlobalSubscriber<BackgroundServiceBinder>() {
            override fun onNext(t: BackgroundServiceBinder) {
                backgroundServiceBinder = t
            }
        })

        bgService.flatMap { Observable.combineLatest(
                it.roomState.flatMap { state ->
                    Observable.combineLatest(
                            appComponent.roomRepository.optRoomWithMembers(state.currentRoomID),
                            appComponent.userRepository.optUser(state.currentRoomActiveSpeakerID),
                            { first, second -> RoomData(state, first, second) })
                },
                it.loginState,
                context.getConnectivity(),
                { first, second, third -> first.pairWith(second).tripleWith(third) })
        }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribe(object : GlobalSubscriber<Triple<RoomData, LoginState, Boolean>>() {
                    override fun onNext(t: Triple<RoomData, LoginState, Boolean>) {
                        updateRoomState(t.first, t.second, t.third)
                    }
                })

        bgService.connect()
    }

    internal fun updateRoomState(roomData: RoomData, loginState: LoginState, hasConnectivity: Boolean) {
        adapter.setMembers(roomData.roomWithMembers?.members ?: emptyList(), if (hasConnectivity) roomData.roomState.currentRoomOnlineMemberIDs else emptyList())
        views?.apply {
            pttBtn.roomState = if (hasConnectivity) roomData.roomState else roomData.roomState.copy(status = RoomState.Status.IDLE)
            toolbar.title = roomData.roomWithMembers?.getRoomName(context)

            val show: Boolean

            if (roomData.roomState.currentRoomActiveSpeakerID == null) {
                show = false
            } else if (roomData.roomState.currentRoomActiveSpeakerID == loginState.currentUserID) {
                show = true
                roomStatusView.text = R.string.room_talking.toFormattedString(context)
            } else if (roomData.currentActiveUser != null) {
                show = true
                roomStatusView.text = R.string.room_other_is_talking.toFormattedString(context, roomData.currentActiveUser.name)
            } else {
                show = false
            }

            roomStatusView.animate().alpha(if (show) 1f else 0f).start()
        }
    }

    override fun onStop() {
        backgroundServiceBinder = null

        super.onStop()
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun requestMic() {
        backgroundServiceBinder?.requestMic()?.subscribe(GlobalSubscriber())
    }

    override fun releaseMic() {
        backgroundServiceBinder?.releaseMic()?.subscribe(GlobalSubscriber())
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

    internal data class RoomData(val roomState: RoomState,
                                 val roomWithMembers: RoomWithMembers?,
                                 val currentActiveUser: User?)

    private class ViewHolder(container: ViewGroup,
                             val imageView: ImageView = LayoutInflater.from(container.context).inflate(R.layout.view_room_member_item, container, false) as ImageView)
    : RecyclerView.ViewHolder(imageView)

    interface Callbacks {
        fun onRoomQuited()
    }
}