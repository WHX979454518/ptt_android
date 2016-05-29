package com.xianzhitech.ptt.media

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.support.annotation.RawRes
import android.util.SparseIntArray
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.engine.TalkEngine
import com.xianzhitech.ptt.engine.TalkEngineProvider
import com.xianzhitech.ptt.engine.WebRtcTalkEngine
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.*
import com.xianzhitech.ptt.ui.ActivityProvider
import rx.Observable
import rx.subjects.BehaviorSubject

class AudioHandler(private val appContext: Context,
                   private val signalService: SignalService,
                   private val talkEngineProvider: TalkEngineProvider,
                   private val activityProvider: ActivityProvider) {

    companion object {
//        private val BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

//        const val MESSAGE_PUSH_DOWN = "+PTT=P"
//        const val MESSAGE_PUSH_RELEASE = "+PTT=R"
    }

    private val audioManager : AudioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val currentBluetoothDevice = BehaviorSubject.create<BluetoothDevice>(null as BluetoothDevice?)
    //    private var mediaSession : MediaSessionCompat? = null
    private var currentTalkEngine : TalkEngine? = null
    private val soundPool: Pair<SoundPool, SparseIntArray> by lazy {
        Pair(SoundPool(1, AudioManager.STREAM_VOICE_CALL, 0), SparseIntArray()).apply {
            second.put(R.raw.incoming, first.load(appContext, R.raw.incoming, 0))
            second.put(R.raw.outgoing, first.load(appContext, R.raw.outgoing, 0))
            second.put(R.raw.over, first.load(appContext, R.raw.over, 0))
            second.put(R.raw.pttup, first.load(appContext, R.raw.pttup, 0))
            second.put(R.raw.pttup_offline, first.load(appContext, R.raw.pttup_offline, 0))
        }
    }

    init {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (audioManager.isBluetoothScoAvailableOffCall && bluetoothAdapter != null) {
            initializeBluetooth(bluetoothAdapter)
        } else {
            logd("Bluetooth SCO not supported")
        }

        signalService.loginStatus
                .map { it != LoginStatus.IDLE }
                .distinctUntilChanged()
                .observeOnMainThread()
                .subscribeSimple {
                    if (it) {
                        MediaButtonReceiver.registerMediaButtonEvent(appContext)
                    }
                    else {
                        MediaButtonReceiver.unregisterMediaButtonEvent(appContext)
                    }
                }

        // 在房间处于活动状态时, 请求系统的音频响应
        signalService.roomState
                .distinctUntilChanged { it.speakerId }
                .subscribeSimple {
                    if (it.speakerId != null) {
                        val hint = if (Build.VERSION.SDK_INT >= 19 && it.speakerId == signalService.peekLoginState().currentUserID) {
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                        } else {
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                        }

                        audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, hint)
                    }
                    else {
                        audioManager.abandonAudioFocus(null)
                    }
                }

        // 当进入房间时, 连接当前的蓝牙设备到SCO上, 退出时则关闭
        Observable.combineLatest(
                signalService.roomStatus.distinctUntilChanged { it.inRoom },
                signalService.loginState.distinctUntilChanged { it.currentUserID },
                currentBluetoothDevice,
                { status, loginState, device -> Triple(status, loginState, device)})
                .observeOnMainThread()
                .subscribeSimple {
                    val (status, loginState, device) = it
                    if (status.inRoom) {
                        if (device != null) {
                            audioManager.isSpeakerphoneOn = false
                            logd("SPEAKER: Turning off to enable bluetooth")
                            audioManager.mode = AudioManager.MODE_NORMAL
                            logd("SCO: Turning on bluetooth sco")
                            startSco()
                        }
                        else {
                            stopSco()
                            logd("SPEAKER: Turning on because bluetooth disconnected")
                            audioManager.isSpeakerphoneOn = true
                            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        }
                    }
                    else if (loginState.currentUserID == null || device == null) {
                        logd("SCO: Turning off bluetooth sco")
                        logd("SPEAKER: Turning off because not in room")
                        audioManager.isSpeakerphoneOn = false
                        stopSco()
                    }
                }


        // 绑定talk engine震动和提示音
        signalService.roomState
                .distinctUntilChanged { it.voiceServer }
                .subscribeSimple {
                    if (it.voiceServer.isNotEmpty()) {
                        currentTalkEngine?.dispose()
                        currentTalkEngine = talkEngineProvider.createEngine().apply {
                            connect(it.currentRoomId!!, mapOf(WebRtcTalkEngine.PROPERTY_LOCAL_USER_ID to signalService.currentUserId,
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_ADDRESS to it.voiceServer["host"],
                                    WebRtcTalkEngine.PROPERTY_REMOTE_SERVER_PORT to it.voiceServer["port"],
                                    WebRtcTalkEngine.PROPERTY_PROTOCOL to it.voiceServer["protocol"]))
                        }
                    } else {
                        currentTalkEngine?.dispose()
                        currentTalkEngine = null
                    }
                }

        var lastRoomState = signalService.peekRoomState()

        signalService.roomState
                .distinctUntilChanged { it.status }
                .subscribeSimple {
                    when (it.status) {
                        RoomStatus.ACTIVE -> onMicActivated(it.speakerId == signalService.currentUserId)
                        RoomStatus.JOINED -> {
                            when (lastRoomState.status) {
                                RoomStatus.ACTIVE -> onMicReleased(lastRoomState.speakerId == signalService.currentUserId)
                                RoomStatus.REQUESTING_MIC -> {
                                    // 抢麦失败
                                    playSound(R.raw.pttup_offline)
                                }
                                else -> {}
                            }
                        }
                        else -> {}
                    }

                    lastRoomState = it
                }
    }

    private fun initializeBluetooth(bluetoothAdapter: BluetoothAdapter) {
        signalService.roomState
                .distinctUntilChanged { it.status }
                .subscribeSimple {
                    if (it.status == RoomStatus.ACTIVE && signalService.peekLoginState().currentUserID == it.speakerId) {
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
                    }
                    else {
                        Observable.just(emptyList<BluetoothDevice>())
                    }
                }
                .observeOnMainThread()
                .subscribeSimple {
                    val connectedDevice = it.firstOrNull()
                    val oldDevice = currentBluetoothDevice.value
                    logd("QUERY: Old device $oldDevice, newDevice $connectedDevice")
                    if (oldDevice?.address != connectedDevice?.address) {
                        if (oldDevice != null && connectedDevice == null) {
                            Toast.makeText(appContext, R.string.bluetooth_device_disconnected.toFormattedString(appContext), Toast.LENGTH_LONG).show()
                        } else if (connectedDevice != null) {
                            Toast.makeText(appContext, R.string.bluetooth_device_connected.toFormattedString(appContext), Toast.LENGTH_LONG).show()
                        }

                        logd("QUERY: Confirmed new connected device $connectedDevice")
                        currentBluetoothDevice.onNext(connectedDevice)
                    }
                }

        // 蓝牙发生变化时重新绑定media session
        currentBluetoothDevice.distinctUntilChanged()
                .filter { it != null }
                .observeOnMainThread()
                .subscribeSimple {
                    MediaButtonReceiver.registerMediaButtonEvent(activityProvider.currentStartedActivity ?: appContext)
                }
    }

    private fun onMicActivated(isSelf: Boolean) {
        if (isSelf) {
            playSound(R.raw.outgoing)
            currentTalkEngine?.startSend()
        } else {
            playSound(R.raw.incoming)
        }
    }

    private fun onMicReleased(isSelf: Boolean) {
        if (isSelf) {
            playSound(R.raw.pttup)
            currentTalkEngine?.stopSend()
        } else {
            playSound(R.raw.over)
        }
    }

    class RemoteControlClientReceiver : BroadcastReceiver() {
        override fun onReceive(content: Context, intent: Intent) {
        }
    }

    private fun playSound(@RawRes res: Int) {
        soundPool.first.play(soundPool.second[res], 1f, 1f, 1, 0, 1f)
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
    private fun queryBluetoothDevice(btAdapter : BluetoothAdapter) : Observable<Collection<BluetoothDevice>> {
        return getBluetoothProfileConnectedDevices(btAdapter, BluetoothProfile.HEADSET)
                .switchMap { allConnectedDevices ->
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
                .doOnNext { logd("QUERY: Got connected bluetooth devices: ${it.joinToString(",", transform = { it.name })}") }
                .map { it.filter { it.name.contains("PTT") } }
    }

    private fun getBluetoothProfileConnectedDevices(btAdapter: BluetoothAdapter, profileRequested: Int) : Observable<MutableSet<BluetoothDevice>> {
        return Observable.create { subscriber ->
            btAdapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceDisconnected(profile: Int) { }

                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    subscriber.onSingleValue(proxy?.connectedDevices?.toMutableSet() ?: hashSetOf())
                    btAdapter.closeProfileProxy(profile, proxy)
                }
            }, profileRequested)
        }
    }
}