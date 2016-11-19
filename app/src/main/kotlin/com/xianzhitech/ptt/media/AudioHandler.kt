package com.xianzhitech.ptt.media

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.support.annotation.RawRes
import android.support.v4.media.session.MediaSessionCompat
import android.util.SparseIntArray
import android.widget.Toast
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.handler.SignalServiceHandler
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Single
import rx.lang.kotlin.single
import rx.subjects.BehaviorSubject
import java.util.concurrent.atomic.AtomicReference


private val logger = LoggerFactory.getLogger("AudioHandler")

class AudioHandler(private val appContext: Context,
                   private val signalService: SignalServiceHandler,
                   private val mediaButtonHandler: MediaButtonHandler,
                   private val httpClient: OkHttpClient,
                   private val preference : Preference) {


    private val audioManager: AudioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val currentBluetoothDevice = BehaviorSubject.create<BluetoothDevice>(null as BluetoothDevice?)
    //    private var mediaSession : MediaSessionCompat? = null
    private var currentTalkEngine: WebRtcTalkEngine? = null
    private val soundPool: Pair<SoundPool, SparseIntArray> by lazy {
        Pair(SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0), SparseIntArray()).apply {
            second.put(R.raw.incoming, first.load(appContext, R.raw.incoming, 0))
            second.put(R.raw.outgoing, first.load(appContext, R.raw.outgoing, 0))
            second.put(R.raw.over, first.load(appContext, R.raw.over, 0))
            second.put(R.raw.pttup, first.load(appContext, R.raw.pttup, 0))
            second.put(R.raw.pttup_offline, first.load(appContext, R.raw.pttup_offline, 0))
        }
    }

    var highQualityMode : Boolean
    get() = currentTalkEngine?.highQualityMode ?: false
    set(value) {
        currentTalkEngine?.enableHighQualityMode(value)
    }

    init {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (audioManager.isBluetoothScoAvailableOffCall && bluetoothAdapter != null) {
            initializeBluetooth(bluetoothAdapter)
        } else {
            logger.w { "Bluetooth SCO not supported" }
        }

        // 在房间处于活动状态时, 请求系统的音频响应
        signalService.roomStatus.map { it.inRoom }
                .distinctUntilChanged()
                .observeOnMainThread()
                .subscribeSimple {
                    if (it) {
                        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    } else {
                        audioManager.abandonAudioFocus(null)
                    }
                }

        // 接管系统所有的Media事件
        // 监听蓝牙、 耳机的变化, 一旦变化, 也发出通知
        val sessionRef = AtomicReference<MediaSessionCompat>(null)
        Observable.combineLatest(
                signalService.loggedIn,
                currentBluetoothDevice,
                audioManager.headsetSubject,
                { loggedIn, device, pluggedIn -> loggedIn })
                .observeOnMainThread()
                .subscribeSimple {
                    if (it) {
                        sessionRef.get()?.release()

                        val session = MediaSessionCompat(appContext, "AudioHandler", ComponentName(appContext, MediaButtonReceiver::class.java), null)
                        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
                        session.setMediaButtonReceiver(PendingIntent.getBroadcast(appContext, 1, Intent(appContext, MediaButtonReceiver::class.java), 0))
                        session.setCallback(object : MediaSessionCompat.Callback() {
                            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                                mediaButtonHandler.handleMediaButtonEvent(mediaButtonEvent)
                                return true
                            }
                        })
                        session.addOnActiveChangeListener {
                            if (session.isActive.not() && signalService.peekCurrentUserId != null) {
                                session.isActive = true
                            }
                        }
                        session.isActive = true
                        sessionRef.set(session)
                    } else if (!it && sessionRef.get() != null) {
                        val session = sessionRef.get()
                        session.release()
                        sessionRef.set(null)
                    }
                }


        // 当进入房间时, 连接当前的蓝牙设备到SCO上, 退出时则关闭
        Observable.combineLatest(
                signalService.roomStatus.distinctUntilChanged { it -> it.inRoom },
                signalService.currentUserId,
                audioManager.headsetSubject,
                currentBluetoothDevice,
                { status, userId, headsetPluggedIn, device -> Triple(status, headsetPluggedIn, device) })
                .observeOnMainThread()
                .subscribeSimple {
                    val (status, headsetPluggedIn, device) = it
                    if (status.inRoom) {
                        if (device != null) {
                            audioManager.isSpeakerphoneOn = false
                            logger.d { "SPEAKER: Turning off to enable bluetooth" }
                            audioManager.mode = AudioManager.MODE_NORMAL
                            logger.d { "SCO: Turning on bluetooth sco" }
                            startSco()
                        } else {
                            stopSco()
                            logger.d { "SPEAKER: Turning to ${headsetPluggedIn.not()}" }
                            audioManager.isSpeakerphoneOn = headsetPluggedIn.not()
                            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        }
                    } else {
                        logger.d { "SPEAKER: Turning to ${headsetPluggedIn.not()}" }
                        audioManager.isSpeakerphoneOn = headsetPluggedIn.not()

                        if (signalService.peekCurrentUserId == null || device == null) {
                            logger.d { "SCO: Turning off bluetooth sco" }
                            stopSco()
                        }
                        audioManager.mode = AudioManager.MODE_NORMAL
                    }
                }


        // 绑定talk engine震动和提示音
        signalService.roomState
                .distinctUntilChanged( { it -> it.voiceServer })
                .subscribeSimple {
                    if (it.voiceServer.isNotEmpty()) {
                        currentTalkEngine?.dispose()
                        currentTalkEngine = WebRtcTalkEngine(appContext, httpClient).apply {
                            connect(it.currentRoomId!!, mapOf(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID to signalService.peekCurrentUserId,
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_ADDRESS to it.voiceServer["host"],
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT to it.voiceServer["port"],
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_TCP_PORT to it.voiceServer["tcpPort"],
                                    WebRtcTalkEngine.PROPERTY_PROTOCOL to it.voiceServer["protocol"]))
                        }
                    } else {
                        currentTalkEngine?.dispose()
                        currentTalkEngine = null
                    }
                }


        // 监听当前的发言人, 如果是自己, 则打开引擎, 否则关闭
        signalService.roomState
                .map { it.speakerId }
                .distinctUntilChanged()
                .subscribeSimple {
                    if (it != null && it == signalService.peekCurrentUserId) {
                        currentTalkEngine?.startSend()
                    } else {
                        currentTalkEngine?.stopSend()
                    }
                }


        var lastRoomState = signalService.peekRoomState()

        signalService.roomState
                .distinctUntilChanged { it -> it.status }
                .subscribeSimple {
                    when (it.status) {
                        RoomStatus.ACTIVE -> onMicActivated(it.speakerId == signalService.peekCurrentUserId)
                        RoomStatus.JOINED -> {
                            when (lastRoomState.status) {
                                RoomStatus.ACTIVE -> onMicReleased(lastRoomState.speakerId == signalService.peekCurrentUserId)
                                RoomStatus.REQUESTING_MIC -> {
                                    // 抢麦失败
                                    playSound(R.raw.pttup_offline)
                                }
                                else -> {
                                }
                            }
                        }
                        else -> {
                        }
                    }

                    lastRoomState = it
                }
    }


    private fun initializeBluetooth(bluetoothAdapter: BluetoothAdapter) {
        signalService.roomState
                .distinctUntilChanged { it -> it.status }
                .subscribeSimple {
                    if (it.status == RoomStatus.ACTIVE && signalService.peekCurrentUserId == it.speakerId) {
                        audioManager.startBluetoothSco()
                    }
                }

        // 在用户登陆时监听蓝牙设备
        signalService.loginStatus
                .map { it != LoginStatus.IDLE }
                .distinctUntilChanged()
                .switchMap { loggedIn ->
                    if (loggedIn) {
                        queryBluetoothDevice(bluetoothAdapter)
                    } else {
                        Observable.just(emptyList<BluetoothDevice>())
                    }
                }
                .observeOnMainThread()
                .subscribeSimple {
                    val connectedDevice = it.firstOrNull()
                    val oldDevice = currentBluetoothDevice.value
                    logger.d { "QUERY: Old device $oldDevice, newDevice $connectedDevice" }
                    if (oldDevice?.address != connectedDevice?.address) {
                        if (oldDevice != null && connectedDevice == null) {
                            Toast.makeText(appContext, R.string.bluetooth_device_disconnected.toFormattedString(appContext), Toast.LENGTH_LONG).show()
                        } else if (connectedDevice != null) {
                            Toast.makeText(appContext, R.string.bluetooth_device_connected.toFormattedString(appContext), Toast.LENGTH_LONG).show()
                        }

                        logger.d { "QUERY: Confirmed new connected device $connectedDevice" }
                        currentBluetoothDevice.onNext(connectedDevice)
                    }
                }
    }

    private fun onMicActivated(isSelf: Boolean) {
        if (isSelf) {
            playSound(R.raw.outgoing)
        } else {
            playSound(R.raw.incoming)
        }
    }

    private fun onMicReleased(isSelf: Boolean) {
        if (isSelf) {
            playSound(R.raw.pttup)
        } else {
            playSound(R.raw.over)
        }
    }

    private fun playSound(@RawRes res: Int) {
        if (preference.playIndicatorSound) {
            soundPool.first.play(soundPool.second[res], 1f, 1f, 1, 0, 1f)
        }
    }

    private fun startSco() {
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
    }

    private fun stopSco() {
        audioManager.isBluetoothScoOn = false
        audioManager.stopBluetoothSco()
    }

    /**
     * 查找已连接的手咪设备
     */
    private fun queryBluetoothDevice(btAdapter: BluetoothAdapter): Observable<Collection<BluetoothDevice>> {
        return getBluetoothProfileConnectedDevices(btAdapter, BluetoothProfile.HEADSET)
                .flatMapObservable { allConnectedDevices ->
                    appContext.receiveBroadcasts(false, BluetoothDevice.ACTION_ACL_CONNECTED)
                            .map {
                                val connectedDevice = it.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                if (allConnectedDevices.indexOfFirst { it.address == connectedDevice.address } < 0) {
                                    allConnectedDevices.add(connectedDevice)
                                }

                                allConnectedDevices.toList()
                            }.mergeWith(appContext.receiveBroadcasts(false, BluetoothDevice.ACTION_ACL_DISCONNECTED)
                            .map {
                                val disconnectedDevice = it.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                allConnectedDevices.removeAll { it.address == disconnectedDevice.address }
                                allConnectedDevices.toList()
                            })
                            .startWith(allConnectedDevices.toList())
                }
                .doOnNext { logger.d { "QUERY: Got connected bluetooth devices: ${it.joinToString(",", transform = { it.name })}" } }
                .map { it.toList() }
    }

    private fun getBluetoothProfileConnectedDevices(btAdapter: BluetoothAdapter, profileRequested: Int): Single<MutableSet<BluetoothDevice>> {
        return single { subscriber ->
            btAdapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceDisconnected(profile: Int) {
                }

                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    subscriber.onSuccess(proxy?.connectedDevices?.toMutableSet() ?: hashSetOf())
                    btAdapter.closeProfileProxy(profile, proxy)
                }
            }, profileRequested)
        }
    }

    private val AudioManager.headsetSubject: Observable<Boolean>
        get() = appContext.receiveBroadcasts(false, AudioManager.ACTION_HEADSET_PLUG)
                .map { it.extras.getInt("state", 0) == 1 }
                .startWith(isWiredHeadsetOn)
                .distinctUntilChanged()
}

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.appComponent.mediaButtonHandler.handleMediaButtonEvent(intent)
    }
}