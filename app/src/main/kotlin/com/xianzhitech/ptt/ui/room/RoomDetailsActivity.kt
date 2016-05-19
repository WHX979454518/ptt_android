package com.xianzhitech.ptt.ui.room

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.wefika.flowlayout.FlowLayout
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.findView
import com.xianzhitech.ptt.ext.observeOnMainThread
import com.xianzhitech.ptt.ui.base.BaseToolbarActivity

class RoomDetailsActivity : BaseToolbarActivity() {
    private lateinit var memberView : FlowLayout
    private lateinit var allMemberLabelView : TextView
    private lateinit var roomNameView : TextView
    private lateinit var deleteRoomButton : View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_room_details)

        memberView = findView(R.id.roomDetails_members)
        allMemberLabelView = findView(R.id.roomDetails_allMemberLabel)
        roomNameView = findView(R.id.roomDetails_name)
        deleteRoomButton = findView(R.id.roomDetails_deleteRoom)
    }

    override fun onStart() {
        super.onStart()

        val appComponent = application as AppComponent
        appComponent.roomRepository.getRoom(intent.getStringExtra(EXTRA_ROOM_ID))
                .observe()
                .observeOnMainThread()
                .doOnNext {
                    if (it == null) {

                    }
                }
    }

    companion object {
        const val EXTRA_ROOM_ID = "room_id"
    }
}