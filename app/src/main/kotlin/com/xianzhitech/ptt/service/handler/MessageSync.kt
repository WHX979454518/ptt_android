package com.xianzhitech.ptt.service.handler

import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.api.dto.MessageQuery
import com.xianzhitech.ptt.ext.i
import com.xianzhitech.ptt.ext.logErrorAndForget
import io.reactivex.Completable
import io.reactivex.Observable
import org.slf4j.LoggerFactory


class MessageSync(private val appComponent: AppComponent) {
    private val logger = LoggerFactory.getLogger("MessageSync")

    init {
        appComponent.signalBroker.connectionState
                .filter { it == SignalApi.ConnectionState.CONNECTED }
                .flatMapCompletable {
                    appComponent.signalBroker.queryMessages(listOf(MessageQuery()))
                            .flatMapObservable { results ->
                                val roomIds = hashSetOf<String>()
                                results.forEach {  result ->
                                    result.data.forEach { msg ->
                                        roomIds.add(msg.roomId)
                                    }
                                }
                                ensureRooms(roomIds).andThen(Observable.fromIterable(results))
                            }
                            .flatMapCompletable {
                                appComponent.storage.saveMessages(it.data)
                            }
                            .logErrorAndForget()
                }
                .logErrorAndForget()
                .subscribe()
    }

    private fun ensureRooms(roomIds : Collection<String>) : Completable {
        return appComponent.storage.getRooms(roomIds)
                .firstElement()
                .flatMapCompletable { rooms ->
                    val roomIdSet = roomIds.toHashSet()
                    rooms.forEach { roomIdSet.remove(it.id) }

                    logger.i { "Downloading room info for ($roomIdSet)" }

                    Observable.fromIterable(roomIdSet)
                            .flatMapSingle(appComponent.signalBroker::updateRoom)
                            .ignoreElements()
                }
    }
}