package com.xianzhitech.ptt.ui.room

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.callbacks
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ext.subscribeSimple
import com.xianzhitech.ptt.ext.toFormattedString
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.ui.base.BackPressable
import com.xianzhitech.ptt.ui.base.BaseFragment
import com.xianzhitech.ptt.ui.dialog.AlertDialogFragment

class RoomFragment : BaseFragment()
        , BackPressable
        , AlertDialogFragment.OnPositiveButtonClickListener
        , AlertDialogFragment.OnNegativeButtonClickListener
        , AlertDialogFragment.OnNeutralButtonClickListener {

    private class Views(rootView: View)

    private var views: Views? = null
    private lateinit var roomRepository: RoomRepository
    private var joiningProgressDialog : ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        roomRepository = (activity.application as AppComponent).roomRepository
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_room, container, false)?.apply {
            views = Views(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.room, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.room_exit) {
            (context.applicationContext as AppComponent).signalService.leaveRoom().subscribeSimple()
            activity?.finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        val appComponent = context.applicationContext as AppComponent

        appComponent.signalService.roomState.distinctUntilChanged { it.currentRoomId }
                .flatMap { appComponent.roomRepository.getRoomName(it.currentRoomId, excludeUserIds = arrayOf(appComponent.signalService.peekLoginState().currentUserID!!)).observe() }
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

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun onPositiveButtonClicked(fragment: AlertDialogFragment) { }

    override fun onNegativeButtonClicked(fragment: AlertDialogFragment) { }

    override fun onNeutralButtonClicked(fragment: AlertDialogFragment) { }

    interface Callbacks {
        fun onRoomLoaded(name: CharSequence)
        fun onRoomQuited()
    }
}