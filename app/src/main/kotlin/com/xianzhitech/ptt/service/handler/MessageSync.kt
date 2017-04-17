package com.xianzhitech.ptt.service.handler

import com.xianzhitech.ptt.AppComponent


class MessageSync(appComponent: AppComponent) {
    init {
//        appComponent.signalBroker.connectionState
//                .map { it == SignalApi.ConnectionState.CONNECTED }
//                .flatMapCompletable {
//                    appComponent.storage.getAllRoomInfo()
//                            .firstElement()
//                            .flatMapSingle {
//                                val messageQueries = it.values.map { info ->
//                                    MessageQuery(roomId = info.roomId, startTime = info.lastMessageSyncTime?.time, endTime = null)
//                                }
//
//                                appComponent.signalBroker.queryMessages(messageQueries)
//                            }
//                            .flatMapObservable { Observable.fromIterable(it) }
//                            .flatMapCompletable {
//                                // Do we have this room? Check first...
//                                appComponent.storage.updateRoomMessages(it.roomId, it.data, it.syncTime)
//                            }
//                }
//                .logErrorAndForget()
//                .subscribe()
    }
}