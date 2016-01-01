package com.xianzhitech.ptt.service.provider

import android.util.ArrayMap
import rx.subjects.BehaviorSubject
import kotlin.collections.emptyList
import kotlin.collections.getOrPut

/**
 * Created by fanchao on 1/01/16.
 */
abstract class AbstractSignalProvider : SignalProvider {
    private val activeMemberSubjects = ArrayMap<String, BehaviorSubject<Collection<String>>>()
    private val currentSpeakerSubjects = ArrayMap<String, BehaviorSubject<String>>()

    fun ensureActiveMemberSubject(conversationId: String) = synchronized(activeMemberSubjects, {
        activeMemberSubjects.getOrPut(conversationId, { BehaviorSubject.create() })
    })

    fun ensureCurrentSpeakerSubject(conversationId: String) = synchronized(currentSpeakerSubjects, {
        currentSpeakerSubjects.getOrPut(conversationId, { BehaviorSubject.create() })
    })

    override fun getActiveMemberIds(conversationId: String) = ensureActiveMemberSubject(conversationId)
    override fun peekActiveMemberIds(conversationId: String) = ensureActiveMemberSubject(conversationId).value ?: emptyList<String>()

    override fun getCurrentSpeakerId(conversationId: String) = ensureCurrentSpeakerSubject(conversationId)
    override fun peekCurrentSpeakerId(conversationId: String) = currentSpeakerSubjects[conversationId]?.value
}