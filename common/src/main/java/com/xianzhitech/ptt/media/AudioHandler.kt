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
import com.google.common.base.Optional
import com.xianzhitech.ptt.Preference
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.broker.SignalBroker
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.LoginStatus
import com.xianzhitech.ptt.service.RoomState
import com.xianzhitech.ptt.service.RoomStatus
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function3
import io.reactivex.functions.Function4
import io.reactivex.subjects.BehaviorSubject
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicReference


private val logger = LoggerFactory.getLogger("AudioHandler")

class AudioHandler(private val appContext: Context,
                   private val signalService: SignalBroker,
                   private val mediaButtonHandler: MediaButtonHandler,
                   private val httpClient: OkHttpClient,
                   private val preference: Preference) {


    private val audioManager: AudioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val currentBluetoothDevice = BehaviorSubject.createDefault(Optional.absent<BluetoothDevice>())
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

    private val roomStatus: Observable<RoomStatus>
        get() = signalService.currentWalkieRoomState.map(RoomState::status).distinctUntilChanged()

    private val loggedIn: Observable<Boolean>
        get() = signalService.currentUser.map { it.isPresent }.distinctUntilChanged()

    init {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (audioManager.isBluetoothScoAvailableOffCall && bluetoothAdapter != null) {
            initializeBluetooth(bluetoothAdapter)
        } else {
            logger.w { "Bluetooth SCO not supported" }
        }

        // 在房间处于活动状态时, 请求系统的音频响应
        roomStatus.map(RoomStatus::inRoom)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (it) {
                        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    } else {
                        audioManager.abandonAudioFocus(null)
                    }
                }

        // 接管系统所有的Media事件
        // 监听蓝牙、 耳机的变化, 一旦变化, 也发出通知
        val sessionRef = AtomicReference<MediaSessionCompat>(null)
        val mediaObservable: Observable<Boolean> = Observable.combineLatest(
                loggedIn,
                currentBluetoothDevice,
                audioManager.headsetSubject,
                Function3 { loggedIn, _, _ -> loggedIn }
        )

        mediaObservable.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
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
                            if (session.isActive.not() && signalService.currentUser.value.isPresent) {
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
        val roomObservable: Observable<Triple<RoomStatus, Boolean, Optional<BluetoothDevice>>> =
                Observable.combineLatest(
                        roomStatus.distinctUntilChanged(RoomStatus::inRoom),
                        signalService.currentUser,
                        audioManager.headsetSubject,
                        currentBluetoothDevice,
                        Function4 { status, _, headsetPluggedIn, device -> Triple(status, headsetPluggedIn, device) })


        roomObservable.observeOn(AndroidSchedulers.mainThread())
                .subscribe { (status, headsetPluggedIn, device) ->
                    if (status.inRoom) {
                        if (device.isPresent) {
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

                        if (signalService.currentUser.value.isAbsent || device.isAbsent) {
                            logger.d { "SCO: Turning off bluetooth sco" }
                            stopSco()
                        }
                        audioManager.mode = AudioManager.MODE_NORMAL
                    }
                }


        // 绑定talk engine震动和提示音
        signalService.currentWalkieRoomState
                .distinctUntilChanged(RoomState::voiceServer)
                .subscribe {
                    if (it.voiceServer != null) {
                        currentTalkEngine?.dispose()
                        currentTalkEngine = WebRtcTalkEngine(appContext, httpClient).apply {
                            connect(it.currentRoomId!!, mapOf(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID to signalService.currentUser.value.get().id,
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_ADDRESS to it.voiceServer.host,
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT to it.voiceServer.port,
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_TCP_PORT to it.voiceServer.tcpPort,
                                    WebRtcTalkEngine.PROPERTY_PROTOCOL to it.voiceServer.protocol))
                        }
                    } else {
                        currentTalkEngine?.dispose()
                        currentTalkEngine = null
                    }
                }


        // 监听当前的发言人, 如果是自己, 则打开引擎, 否则关闭
        signalService.currentWalkieRoomState
                .distinctUntilChanged(RoomState::speakerId)
                .subscribe {
                    if (it.speakerId != null && it.speakerId == signalService.peekUserId()) {
                        currentTalkEngine?.startSend()
                    } else {
                        currentTalkEngine?.stopSend()
                    }
                }


        var lastRoomState = signalService.currentWalkieRoomState.value

        signalService.currentWalkieRoomState
                .distinctUntilChanged(RoomState::status)
                .subscribe {
                    when (it.status) {
                        RoomStatus.ACTIVE -> onMicActivated(it.speakerId == signalService.peekUserId())
                        RoomStatus.JOINED -> {
                            when (lastRoomState.status) {
                                RoomStatus.ACTIVE -> onMicReleased(lastRoomState.speakerId == signalService.peekUserId())
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
        signalService.currentWalkieRoomState
                .distinctUntilChanged(RoomState::status)
                .subscribe {
                    if (it.status == RoomStatus.ACTIVE && signalService.peekUserId() == it.speakerId) {
                        audioManager.startBluetoothSco()
                    }
                }

        // 在用户登陆时监听蓝牙设备
        loggedIn.switchMap { loggedIn ->
            if (loggedIn) {
                queryBluetoothDevice(bluetoothAdapter)
            } else {
                Observable.just(emptyList<BluetoothDevice>())
            }
        }.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val connectedDevice = it.firstOrNull()
                    val oldDevice = currentBluetoothDevice.value.orNull()
                    logger.d { "QUERY: Old device $oldDevice, newDevice $connectedDevice" }
                    if (oldDevice?.address != connectedDevice?.address) {
                        if (oldDevice != null && connectedDevice == null) {
                            Toast.makeText(appContext, R.string.bluetooth_device_disconnected.toFormattedString(appContext), Toast.LENGTH_LONG).show()
                        } else if (connectedDevice != null) {
                            Toast.makeText(appContext, R.string.bluetooth_device_connected.toFormattedString(appContext), Toast.LENGTH_LONG).show()
                        }

                        logger.d { "QUERY: Confirmed new connected device $connectedDevice" }
                        currentBluetoothDevice.onNext(connectedDevice.toOptional())
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
    private fun queryBluetoothDevice(btAdapter: BluetoothAdapter): Observable<List<BluetoothDevice>> {
        return getBluetoothProfileConnectedDevices(btAdapter, BluetoothProfile.HEADSET)
                .flatMapObservable { initialConnectedDevices ->
                    val allConnectedDevices = TreeSet<BluetoothDevice> { d1, d2 -> d1.address.compareTo(d2.address) }
                    allConnectedDevices.addAll(initialConnectedDevices)

                    appContext.receiveBroadcasts(BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED)
                            .map {
                                val device = it.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                if (it.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                                    allConnectedDevices.add(device)
                                }
                                else {
                                    allConnectedDevices.remove(device)
                                }

                                allConnectedDevices.toList()
                            }
                            .startWith(initialConnectedDevices)
                }
                .doOnNext { logger.d { "QUERY: Got connected bluetooth devices: ${it.joinToString(",", transform = { it.name })}" } }
    }

    private fun getBluetoothProfileConnectedDevices(btAdapter: BluetoothAdapter, profileRequested: Int): Single<List<BluetoothDevice>> {
        return Single.create { subscriber ->
            btAdapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceDisconnected(profile: Int) {
                }

                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    subscriber.onSuccess(proxy?.connectedDevices ?: emptyList())
                    btAdapter.closeProfileProxy(profile, proxy)
                }
            }, profileRequested)
        }
    }

    private val AudioManager.headsetSubject: Observable<Boolean>
        get() = appContext.receiveBroadcasts(AudioManager.ACTION_HEADSET_PLUG)
                .map { it.extras.getInt("state", 0) == 1 }
                .startWith(isWiredHeadsetOn)
                .distinctUntilChanged()
}

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.appComponent.mediaButtonHandler.handleMediaButtonEvent(intent)
    }
}