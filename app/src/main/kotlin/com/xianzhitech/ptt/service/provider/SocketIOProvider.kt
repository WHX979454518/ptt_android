package com.xianzhitech.ptt.service.provider

import android.os.Handler
import android.os.Looper
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.model.Group
import com.xianzhitech.ptt.model.Room
import com.xianzhitech.ptt.model.User
import com.xianzhitech.ptt.presenter.RoomPresenter
import com.xianzhitech.ptt.repo.ContactRepository
import com.xianzhitech.ptt.repo.GroupRepository
import com.xianzhitech.ptt.repo.RoomRepository
import com.xianzhitech.ptt.repo.UserRepository
import com.xianzhitech.ptt.service.InvalidSavedTokenException
import com.xianzhitech.ptt.service.UserNotLogonException
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import org.json.JSONObject
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import rx.subscriptions.Subscriptions
import java.io.Serializable
import java.util.regex.Pattern
import kotlin.collections.listOf
import kotlin.collections.mapOf

/**
 *
 * 用Socket.io写的一个服务器
 *
 * Created by fanchao on 17/12/15.
 */
class SocketIOProvider(private val userRepository: UserRepository,
                       private val groupRepository: GroupRepository,
                       private val roomRepository: RoomRepository,
                       private val contactRepository: ContactRepository,
                       private val endpoint: String) : AbstractSignalProvider(), AuthProvider {
    companion object {
        public const val EVENT_SERVER_USER_LOGON = "s_logon"
        public const val EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED = "s_member_update"
        public const val EVENT_SERVER_SPEAKER_CHANGED = "s_speaker_changed"
        public const val EVENT_SERVER_ROOM_INFO_CHANGED = "s_room_summary"
        public const val EVENT_SERVER_INVITE_TO_JOIN = "s_invite_to_join"

        public const val EVENT_CLIENT_SYNC_CONTACTS = "c_sync_contact"
        public const val EVENT_CLIENT_CREATE_ROOM = "c_create_room"
        public const val EVENT_CLIENT_JOIN_ROOM = "c_join_room"
        public const val EVENT_CLIENT_LEAVE_ROOM = "c_leave_room"
        public const val EVENT_CLIENT_CONTROL_MIC = "c_control_mic"
        public const val EVENT_CLIENT_RELEASE_MIC = "c_release_mic"
    }

    private lateinit var socket: Socket
    private val logonUser = BehaviorSubject.create(null as User?)
    private val errorSubject = PublishSubject.create<Any>()
    private val handler = Handler(Looper.getMainLooper())

    var roomPresenter : RoomPresenter? = null

    override fun createRoom(request: JoinRoomFromContact): Observable<Room> {
        return socket
                .sendEvent(EVENT_CLIENT_CREATE_ROOM, { it[0] as JSONObject }, request.toJSON())
                .flatMap {
                    roomRepository.updateRoom(Room().readFrom(it), it.getJSONArray("members").toStringIterable())
                }
    }

    private fun <T> reifiedErrorSubject(): PublishSubject<T> = (errorSubject as PublishSubject<T>)

    override fun deleteRoom(roomId: String): Observable<Unit> {
        return Observable.empty()
    }

    override fun peekCurrentLogonUser(): User? = logonUser.value

    override fun joinRoom(roomId: String): Observable<JoinRoomResult> {
        return socket.sendEvent(
                EVENT_CLIENT_JOIN_ROOM,
                { it[0] as JSONObject },
                JSONObject().put("roomId", roomId))
                .flatMap { response ->
                    val server = response.getJSONObject("server")
                    val roomInfoObject = response.getJSONObject("roomInfo")
                    val conversation = Room().readFrom(roomInfoObject)
                    val engineProperties = mapOf(Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_IP, server.getString("host")),
                            Pair(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID, peekCurrentLogonUser()?.id ?: throw UserNotLogonException()),
                            Pair(WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT, server.getInt("port")),
                            Pair(WebRtcTalkEngine.PROPERTY_PROTOCOL, server.getString("protocol")))

                    ensureActiveMemberSubject(roomId).onNext(response.getJSONArray("activeMembers").toStringList())
                    ensureCurrentSpeakerSubject(roomId).onNext(response.optString("speaker"))
                    roomRepository.updateRoom(conversation, roomInfoObject.getJSONArray("members").toStringList()).map {
                        JoinRoomResult(roomInfoObject.getString("idNumber"), engineProperties)
                    }
                }
                .mergeWith(reifiedErrorSubject())
    }

    override fun getCurrentLogonUser() = logonUser

    override fun quitConversation(conversationId: String): Observable<Unit> {
        return socket.sendEvent(EVENT_CLIENT_LEAVE_ROOM,
                {}, JSONObject().put("roomId", conversationId))
                .mergeWith(reifiedErrorSubject())
    }

    override fun requestMic(conversationId: String): Observable<Boolean> {
        return socket.sendEvent(EVENT_CLIENT_CONTROL_MIC, { (it[0] as JSONObject).let { it.getBoolean("success") && it.getString("speaker") == peekCurrentLogonUser()?.id } },
                JSONObject().put("roomId", conversationId))
                .doOnNext {
                    if (it) {
                        ensureCurrentSpeakerSubject(conversationId).onNext(peekCurrentLogonUser()?.id)
                    }
                }
                .mergeWith(reifiedErrorSubject())
    }

    override fun releaseMic(conversationId: String): Observable<Unit> {
        return socket.sendEvent(EVENT_CLIENT_RELEASE_MIC, {}, JSONObject().put("roomId", conversationId))
                .doOnSubscribe {
                    ensureCurrentSpeakerSubject(conversationId).let {
                        if (it.value == peekCurrentLogonUser()?.id) {
                            it.onNext(null)
                        }
                    }
                }
    }

    override fun resumeLogin(token: Serializable): Observable<LoginResult> {
        if (token is String) {
            val matcher = Pattern.compile("(.+?):(.+?)").matcher(token)
            if (matcher.matches()) {
                return login(matcher.group(1).decodeBase64(), matcher.group(2).decodeBase64())
            }
        }

        return Observable.error(InvalidSavedTokenException())
    }

    override fun login(username: String, password: String) = doLogin {
        it.put("Authorization", listOf("Basic ${(username + ':' + password.toMD5()).toBase64()}"))
    }.map { LoginResult(it, "${username.toBase64()}:${password.toBase64()}") }

    private fun doLogin(headerOperator: (MutableMap<String, List<String>>) -> Unit): Observable<User> {
        return IO.socket(endpoint).let {
            Observable.create<User>({ subscriber ->

                // 绑定IO事件,以便操作Headers
                it.io().on(Manager.EVENT_TRANSPORT, {
                    (it[0] as Transport).on(Transport.EVENT_REQUEST_HEADERS, { headerOperator(it[0] as MutableMap<String, List<String>>) })
                })

                // 监听用户登陆事件
                it.on(EVENT_SERVER_USER_LOGON, { args ->
                    try {
                        subscriber.onNext(parseServerResult(args, { it -> User().readFrom(it[0] as JSONObject) }))
                    } catch(e: Exception) {
                        subscriber.onError(e)
                        subscriber.onCompleted()
                    }
                })

                // 监听出错事件
                //                val errorHandler: (Array<Any>) -> Unit = { errorSubject.onError(ServerException(it.getOrNull(0)?.toString() ?: "Unknown exception: " + it)) }
                //                it.on(Socket.EVENT_ERROR, errorHandler)
                //                it.on(Socket.EVENT_CONNECT_ERROR, errorHandler)
                //                it.on(Socket.EVENT_CONNECT_TIMEOUT, { errorSubject.onError(TimeoutException()) })

                // 监听房间在线成员变化事件
                it.on(EVENT_SERVER_ROOM_ACTIVE_MEMBER_UPDATED, {
                    val event = parseServerResult(it, { it[0] as JSONObject })
                    ensureActiveMemberSubject(event.getString("roomId"))
                            .onNext(event.getJSONArray("activeMembers").toStringList())
                })

                // 监听得Mic人变化的事件
                it.on(EVENT_SERVER_SPEAKER_CHANGED, {
                    val event = parseServerResult(it, { it[0] as JSONObject })
                    ensureCurrentSpeakerSubject(event.getString("roomId"))
                            .onNext(event.let { if (it.isNull("speaker")) null else it.getString("speaker") })
                })

                // 监听房间成员变化的事件
                it.on(EVENT_SERVER_ROOM_INFO_CHANGED, {
                    logd("RoomInfoChanged: $it[0")
                    val event = parseServerResult(it, { it[0] as JSONObject })
                })

                // 监听房间邀请事件
                it.on(EVENT_SERVER_INVITE_TO_JOIN, {
                    val roomId = parseServerResult(it, { (it[0] as JSONObject) }).getString("roomId")
                    logd("Server invites us to join $roomId")
                    roomPresenter?.let {
                        handler.post {
                            it.requestJoinRoom(JoinRoomFromExisting(roomId), false)
                        }
                    }
                })

                subscriber.add(Subscriptions.create {
                    it.close()
                })

                socket = it.connect()
            }).flatMap { person ->
                logonUser.onNext(person) // 设置当前用户
                syncContacts().map { person } // 在登陆完成以后等待通讯录同步
            }.mergeWith(reifiedErrorSubject<User>())
        }
    }

    override fun logout(): Observable<Unit> {
        logonUser.onNext(null)
        socket.close()
        return Observable.empty()
    }

    fun syncContacts() = socket.sendEvent(EVENT_CLIENT_SYNC_CONTACTS, {
        val result: JSONObject = it[0] as JSONObject
        var groupBuf = Group()

        val groupJsonArray = result.getJSONObject("enterpriseGroups").getJSONArray("add")
        val groups = groupJsonArray.transform { groupBuf.readFrom(it as JSONObject) }
        Triple(persons, groupJsonArray, groups)
    }, JSONObject().put("enterMemberVersion", 1).put("enterGroupVersion", 1))
            .flatMap {
                userRepository.replaceAllUsers(it.first + (logonUser.value ?: throw IllegalStateException("User not logon")))
                        .concatWith(groupRepository.replaceAllGroups(it.third, it.second.toGroupsAndMembers()))
                        .concatWith(contactRepository.replaceAllContacts(it.first.transform { it.id }, it.third.transform { it.id }))
            }
}

