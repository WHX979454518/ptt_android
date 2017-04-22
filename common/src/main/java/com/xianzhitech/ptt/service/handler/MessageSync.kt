package com.xianzhitech.ptt.service.handler

import com.xianzhitech.ptt.AppComponent
import com.xianzhitech.ptt.api.SignalApi
import com.xianzhitech.ptt.api.dto.MessageQuery
import com.xianzhitech.ptt.data.Message
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
                    val query  = MessageQuery(
                            startTime = appComponent.preference.lastMessageSyncDate?.time
                    )

                    appComponent.signalBroker.queryMessages(listOf(query))
                            .flatMapCompletable {
                                val result = it.first()
                                val roomIds = hashSetOf<String>()
                                result.data.mapTo(roomIds, Message::roomId)

                                logger.i { "Received ${roomIds.size} rooms' messages, size = ${result.data.size}" }
                                ensureRooms(roomIds)
                                        .andThen(appComponent.storage.saveMessages(result.data))
                                        .doOnComplete { appComponent.preference.lastMessageSyncDate = result.syncTime }
                            }
                            .logErrorAndForget()
                }
                .logErrorAndForget()
                .subscribe()
    }

    private fun ensureRooms(roomIds : Collection<String>) : Completable {
        return appComponent.storage.getAllRoomIds()
                .firstElement()
                .flatMapCompletable {
                    val existingRoomIds = it.sorted()
                    val roomIdsToDownload = roomIds.filter { existingRoomIds.binarySearch(it) < 0 }


                    logger.i { "Downloading room info for ($roomIdsToDownload)" }

                    Observable.fromIterable(roomIdsToDownload)
                            .flatMapSingle(appComponent.signalBroker::updateRoom)
                            .ignoreElements()
                }
    }
}