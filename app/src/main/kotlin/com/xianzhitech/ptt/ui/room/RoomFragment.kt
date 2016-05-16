package com.xianzhitech.ptt.ui.room

import android.app.ProgressDialog
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.combineWith
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.getConnectivity
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment
import rx.Observable

class RoomFragment : BaseFragment()
        , BackPressable
        , AlertDialogFragment.OnPositiveButtonClickListener
        , AlertDialogFragment.OnNegativeButtonClickListener
        , AlertDialogFragment.OnNeutralButtonClickListener {

    private class Views(rootView: View)
//                        val pttBtn: PushToTalkButton = rootView.findView(R.id.room_pushToTalkButton),
//                        val speakerSourceView: Spinner = rootView.findView(R.id.room_speakerSource),
//                        val memberView: RecyclerView = rootView.findView(R.id.room_memberList))

//    private enum class SpeakerMode(val titleResId: Int) {
//        SPEAKER(R.string.source_speaker),
//        HEADPHONE(R.string.source_headphone),
//        BLUETOOTH(R.string.source_bluetooth)
//    }

    private var views: Views? = null
//    private val adapter = Adapter()
    private lateinit var roomRepository: RoomRepository
    private var joiningProgressDialog : ProgressDialog? = null

//    private val speakSourceAdapter = object : BaseAdapter() {
//        var speakerModes: List<SpeakerMode> = emptyList()
//
//        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
//            val view = (convertView as? TextView) ?: TextView(parent!!.context)
//            view.setText(speakerModes[position].titleResId)
//            return view
//        }
//
//        override fun getItem(position: Int) = speakerModes[position]
//        override fun getItemId(position: Int) = speakerModes[position].ordinal.toLong()
//        override fun getCount() = speakerModes.size
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        speakSourceAdapter.speakerModes = SpeakerMode.values().toList()
        roomRepository = (activity.application as AppComponent).roomRepository
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_room, container, false)?.apply {
            views = Views(this).apply {
//                memberView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
//                memberView.adapter = adapter
//                speakerSourceView.adapter = speakSourceAdapter
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.room, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.room_exit) {
            (context.applicationContext as AppComponent).signalService.quitRoom().subscribeSimple()
            activity?.finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        val appComponent = context.applicationContext as AppComponent

        appComponent.signalService.roomState.flatMap { roomState ->
            Observable.combineLatest(
                    appComponent.roomRepository.getRoom(roomState.currentRoomID).observe(),
                    appComponent.roomRepository.getRoomMembers(roomState.currentRoomID).observe(),
                    appComponent.userRepository.getUser(roomState.currentRoomActiveSpeakerID).getAsync().toObservable(),
                    { room, members, currentActiveUser -> RoomData(roomState, room, members, currentActiveUser) }) }
                .combineWith(context.getConnectivity())
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    updateRoomState(it.first, it.second)
                }

        appComponent.signalService.roomState.distinctUntilChanged { it.currentRoomID }
                .flatMap { appComponent.roomRepository.getRoomName(it.currentRoomID, excludeUserIds = arrayOf(appComponent.signalService.peekLoginState().currentUserID!!)).observe() }
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    callbacks<Callbacks>()?.onRoomLoaded(it.name)
                }

        appComponent.signalService.roomState
                .observeOnMainThread()
                .compose(bindToLifecycle())
                .subscribeSimple {
                    if (it.status == RoomStatus.JOINING && joiningProgressDialog == null) {
                        joiningProgressDialog = ProgressDialog.show(context, R.string.please_wait.toFormattedString(context), R.string.joining_room.toFormattedString(context))
                    }
                    else if (it.status != RoomStatus.JOINING && joiningProgressDialog != null) {
                        joiningProgressDialog!!.dismiss()
                        joiningProgressDialog = null
                    }
                }
    }

    private fun updateRoomState(roomData: RoomData, hasConnectivity: Boolean) {
        val loginState = (context.applicationContext as AppComponent).signalService.peekLoginState()
        logd("updateRoomState, roomState: %s, loginState: %s", roomData.roomState, loginState)
//        adapter.setMembers(roomData.roomMembers, if (hasConnectivity) roomData.roomState.currentRoomOnlineMemberIDs else emptyList())
        views?.apply {
            val show: Boolean

            if (roomData.roomState.currentRoomActiveSpeakerID == null) {
                show = false
            } else if (roomData.roomState.currentRoomActiveSpeakerID == loginState.currentUserID) {
                show = true
            } else if (roomData.currentActiveUser != null) {
                show = true
            } else {
                show = false
            }

            //            roomStatusView.animate().alpha(if (show) 1f else 0f).start()
        }
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) { }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) { }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) { }

//    private class Adapter : RecyclerView.Adapter<ViewHolder>(), Comparator<User> {
//        val activeMembers = hashSetOf<String>()
//        val members = arrayListOf<User>()
//        var currentSpeakerId: String? = null
//            set(newSpeaker) {
//                if (field != newSpeaker) {
//                    field = newSpeaker
//                    notifyDataSetChanged()
//                }
//            }
//
//        fun setMembers(newMembers: Collection<User>, newActiveMembers: Collection<String>) {
//            members.clear()
//            members.addAll(newMembers)
//            members.sortWith(this)
//            activeMembers.clear()
//            activeMembers.addAll(newActiveMembers)
//            notifyDataSetChanged()
//        }
//
//        override fun compare(lhs: User, rhs: User): Int {
//            val lhsActive = activeMembers.contains(lhs.id)
//            val rhsActive = activeMembers.contains(rhs.id)
//            if (lhsActive && rhsActive || (!lhsActive && !rhsActive)) {
//                return lhs.name.compareTo(rhs.name)
//            } else if (lhsActive) {
//                return 1
//            } else {
//                return -1
//            }
//        }
//
//        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = ViewHolder(parent!!)
//
//        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//            holder.imageView.let {
//                val person = members[position]
//                it.setImageDrawable(person.createAvatarDrawable(it.context))
//                it.isEnabled = activeMembers.contains(person.id)
//                it.isSelected = currentSpeakerId == person.id
//            }
//        }
//
//        override fun getItemCount() = members.size
//    }

    private data class RoomData(val roomState: RoomState,
                                val room: Room?,
                                val roomMembers : List<User>,
                                val currentActiveUser: User?)

    private class ViewHolder(container: ViewGroup,
                             rootView: View = LayoutInflater.from(container.context).inflate(R.layout.view_room_member_item, container, false),
                             val imageView: ImageView = rootView.findView(R.id.roomMemberItem_icon)) : RecyclerView.ViewHolder(rootView)

    interface Callbacks {
        fun onRoomLoaded(name: CharSequence)
        fun onRoomQuited()
    }
}