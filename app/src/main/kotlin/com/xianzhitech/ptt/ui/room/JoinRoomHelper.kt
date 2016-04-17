package com.xianzhitech.ptt.ui.room

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.support.v7.app.AlertDialog
import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.Constants
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.service.CreateRoomRequest
import com.xianzhitech.ptt.service.StaticUserException
import rx.Observable
import java.util.concurrent.TimeUnit

/**
 * Created by fanchao on 16/01/16.
 */

fun Context.joinRoom(roomId: String?,
                     createRoomRequest: CreateRoomRequest?,
                     fromInvite: Boolean,
                     lifecycle: Observable.Transformer<in Unit, out Unit>) {
    if (roomId == null && createRoomRequest == null) {
        throw IllegalArgumentException("Either roomId or createRoomRequest has to be non-null")
    } else if (roomId != null && createRoomRequest != null) {
        throw IllegalArgumentException("roomId and createRoomRequest can't be non-null at the same time")
    }

    val appComponent = applicationContext as AppComponent
    val signalService = appComponent.signalService

    ensureConnectivity()
            .flatMap {
                val progressDialog = ProgressDialog(this).apply {
                    setTitle(R.string.please_wait)
                    setMessage(R.string.getting_room_info.toFormattedString(this@joinRoom))
                    setCancelable(false)
                    isIndeterminate = true
                }

                val roomIdObservable = roomId?.let { roomId.toObservable() } ?:
                        signalService.createRoom(createRoomRequest!!)
                                .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                .observeOnMainThread()
                                .doOnSubscribe {
                                    progressDialog.show()
                                }.doOnEach {
                                    progressDialog.dismiss()
                                }

                roomIdObservable.flatMap { appComponent.roomRepository.getRoomWithMembers(it, Constants.MAX_MEMBER_DISPLAY_COUNT) }
                        .map { it ?: throw StaticUserException(R.string.error_unable_to_get_room_info) }
            }
            .flatMap { roomToJoin ->
                signalService.peekRoomState().currentRoomID?.let {
                    appComponent.roomRepository.getRoomWithMembers(it, Constants.MAX_MEMBER_DISPLAY_COUNT)
                            .map { roomToJoin to it }
                } ?: (roomToJoin to null).toObservable()
            }
            .first()
            .flatMap {
                val (roomToJoin, currentRoom) = it
                Observable.create<Room> { subscriber ->
                    if (currentRoom == null || (currentRoom.room.id == roomToJoin.room.id)) {
                        // If the current room is empty, or current joined room is same
                        subscriber.onSingleValue(roomToJoin)
                        return@create
                    }

                    if (fromInvite) {
                        if (currentRoom.room.important && roomToJoin.room.important ||
                                (currentRoom.room.important.not() && roomToJoin.room.important.not())) {
                            // If we receive an invite to join an important room, but current room is also important, prompt to switch
                            // Or, we receive an invite to join an unimportant room, and current room is also unimportant, prompt to switch
                            AlertDialog.Builder(this)
                                    .setTitle(R.string.dialog_confirm_title)
                                    .setMessage(R.string.receive_invite_switch_confirm.toFormattedString(this, currentRoom.getRoomName(this), roomToJoin.getRoomName(this)))
                                    .setPositiveButton(R.string.dialog_yes_switch, { dialog, id ->
                                        dialog.dismiss()
                                        subscriber.onSingleValue(roomToJoin)
                                    })
                                    .setNegativeButton(R.string.dialog_cancel, { dialog, id ->
                                        dialog.dismiss()
                                        subscriber.onCompleted()
                                    })
                                    .show()
                        } else if (roomToJoin.room.important) {
                            // If we receive an invite but current room is unimportant, no confirmation needed, join the room immediately
                            subscriber.onSingleValue(roomToJoin)
                            return@create
                        } else {
                            // currentRoom.room.important
                            // If we receive an unimportant invite but current room is important, ignore the invite
                            subscriber.onCompleted()
                            return@create
                        }
                    } else {
                        if (currentRoom.room.important) {
                            // If current room is important, no new room is allowed to join
                            AlertDialog.Builder(this)
                                    .setTitle(R.string.error_title)
                                    .setMessage(R.string.error_room_is_important_to_quit.toFormattedString(this, currentRoom.getRoomName(this)))
                                    .setNegativeButton(R.string.dialog_ok, { dialog, id ->
                                        dialog.dismiss()
                                        subscriber.onCompleted()
                                    })
                                    .show()
                        } else {
                            // Prompt to switch room
                            AlertDialog.Builder(this)
                                    .setTitle(R.string.dialog_confirm_title)
                                    .setMessage(R.string.room_prompt_switching_message.toFormattedString(this, roomToJoin.getRoomName(this), currentRoom.getRoomName(this)))
                                    .setPositiveButton(R.string.dialog_yes_switch, { dialog, id ->
                                        dialog.dismiss()
                                        subscriber.onSingleValue(roomToJoin)
                                    })
                                    .setNegativeButton(R.string.dialog_cancel, { dialog, id ->
                                        dialog.dismiss()
                                        subscriber.onCompleted()
                                    })
                                    .show()
                        }
                    }
                }.subscribeOnMainThread()
            }
            .flatMap { roomToJoin : Room ->
//                val dialog = ProgressDialog.show(this, R.string.please_wait.toFormattedString(this),
//                        R.string.joining_room.toFormattedString(this), true, false)
                signalService.joinRoom(roomToJoin.id)
                        .timeout(Constants.JOIN_ROOM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .observeOnMainThread()
                        .compose(lifecycle)
                        .doOnEach {
                            if (it.hasThrowable()) {
                                signalService.quitRoom().subscribeSimple()
                            }
//                            dialog.dismiss()
                        }

            }
            .observeOnMainThread()
            .compose(lifecycle)
            .subscribe(object : GlobalSubscriber<Unit>(this@joinRoom) {
                override fun onNext(t: Unit) {
                    startActivity(Intent(this@joinRoom, RoomActivity::class.java))
                }
            })

}