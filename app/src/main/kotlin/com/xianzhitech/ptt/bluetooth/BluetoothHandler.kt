package com.xianzhitech.ptt.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.widget.Toast
import com.xianzhitech.ptt.R
import com.xianzhitech.ptt.ext.*
import com.xianzhitech.ptt.service.RoomStatus
import com.xianzhitech.ptt.service.SignalService
import com.xianzhitech.ptt.service.roomStatus
import rx.Observable
import rx.Scheduler
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.android.schedulers.HandlerScheduler
import rx.subjects.BehaviorSubject
import rx.subscriptions.Subscriptions
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BluetoothHandler(private val appContext: Context,
                       private val signalService: SignalService) {

    companion object {
        private val BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        const val MESSAGE_DEV_PTT_OK = "DEV_PTT_OK"
        const val MESSAGE_DEV_PTT_DISCONNECTED = "DEV_PTT_DISCONNECTED"
        const val MESSAGE_PUSH_DOWN = "+PTT=P"
        const val MESSAGE_PUSH_RELEASE = "+PTT=R"

        private val BT_RETRY_COUNT = 5
    }

    private val audioManager : AudioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val currentBluetoothDevice = BehaviorSubject.create<BluetoothDevice?>()
    private val bluetoothScheduler : Scheduler by lazy {
        HandlerScheduler.from(Handler(HandlerThread("BluetoothIO").apply { start() }.looper))
    }

    init {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (audioManager.isBluetoothScoAvailableOffCall && bluetoothAdapter != null) {
            initializeBluetooth(bluetoothAdapter)
        }
        else {
            logd("Bluetooth SCO not supported")
        }
    }

    private fun initializeBluetooth(bluetoothAdapter: BluetoothAdapter) {
        // 当进入房间时, 连接当前的蓝牙设备到SCO上, 退出时则关闭
        Observable.combineLatest(
                signalService.roomStatus.distinctUntilChanged { it.inRoom },
                currentBluetoothDevice,
                { status, device -> status to device})
                .observeOnMainThread()
                .subscribeSimple {
                    val (status, device) = it
                    if (status.inRoom && device != null) {
                        logd("SCO: Turning on bluetooth sco")
                        startSco()
                    } else if (status.inRoom.not() || device == null) {
                        logd("SCO: Turning off bluetooth sco")
                        stopSco()
                    }
                }


        // 连接并监听蓝牙设备的命令
        currentBluetoothDevice
                .observeOn(bluetoothScheduler)
                .flatMap { device ->
                    if (device == null) {
                        Observable.empty<String>()
                    }
                    else {
                        Observable.create<String> { subscriber ->
                            logd("SOCKET: Connecting to $device socket")
                            try {
                                device.createRfcommSocketToServiceRecord(BT_UUID)?.use { socket ->
                                    if (!socket.isConnected) {
                                        socket.connect()
                                    }
                                    logd("SOCKET: Connected to bluetooth socket")
                                    socket.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                                        val allCommands = listOf(MESSAGE_PUSH_DOWN, MESSAGE_PUSH_RELEASE)
                                        val matchedCommands = allCommands.toMutableList()
                                        var matchedSize = 0

                                        subscriber.add(Subscriptions.create {
                                            logd("SOCKET: Requested closing $device socket")
                                        })

                                        // 一直死循环读取数据直到取消订阅为止
                                        while (subscriber.isUnsubscribed.not() && Thread.interrupted().not()) {
                                            val b: Int
                                            try {
                                                b = reader.read()
                                            } catch(e: Exception) {
                                                break
                                            }

                                            if (b < 0) {
                                                // -1表明这个流已经读完了.
                                                break
                                            }

                                            var char = b.toChar()

                                            // 去除不匹配当前字符的消息
                                            matchedCommands.removeAll { it.length <= matchedSize || it[matchedSize] != char }

                                            if (matchedCommands.isEmpty()) {
                                                throw RuntimeException("Unknown command $char received from bluetooth adapter")
                                            }

                                            matchedSize++

                                            if (matchedCommands.size == 1 && matchedSize == matchedCommands[0].length) {
                                                subscriber.onNext(matchedCommands[0])
                                                matchedSize = 0
                                                matchedCommands.clear()
                                                matchedCommands.addAll(allCommands)
                                            }
                                        }
                                    }
                                }

                                throw IllegalStateException("Connection interrupted")
                            }
                            catch (throwable : Throwable) {
                                subscriber.onError(throwable)
                            }
                        }
                    }
                }
                .retryWhen { attempts ->
                    attempts.flatMap { throwable ->
                        logd("SOCKET: Error while establishing connection to bluetooth device: $throwable. Reconnecting...")
                        Observable.timer(2, TimeUnit.SECONDS, AndroidSchedulers.mainThread()).flatMap {
                            currentBluetoothDevice.filter { it != null }
                        }
                    }
                }
                .subscribeSimple { command ->
                    val roomStatus = signalService.peekRoomState().status

                    when {
                        command == MESSAGE_PUSH_DOWN &&
                                roomStatus == RoomStatus.JOINED -> signalService.requestMic().subscribeSimple()
                        command == MESSAGE_PUSH_RELEASE &&
                                roomStatus.inRoom -> signalService.releaseMic().subscribeSimple()
                    }
                }


        // 绑定媒体按键事件
        retrieveMediaButtonEvent().subscribeSimple()


        // 查找可用设备
        queryBluetoothDevice(bluetoothAdapter)
                .observeOnMainThread()
                .subscribeSimple {
                    val connectedDevice = it.firstOrNull()
                    val oldDevice = currentBluetoothDevice.value
                    logd("QUERY: Old device $oldDevice, newDevice $connectedDevice")
                    if (oldDevice != connectedDevice) {
                        if (oldDevice != null && connectedDevice == null) {
                            Toast.makeText(appContext, R.string.bluetooth_device_disconnected.toFormattedString(appContext), Toast.LENGTH_LONG).show()
                        } else if (connectedDevice != null) {
                            Toast.makeText(appContext, R.string.bluetooth_device_connected.toFormattedString(appContext), Toast.LENGTH_LONG).show()
                        }

                        logd("QUERY: Confirmed new connected device $connectedDevice")
                        currentBluetoothDevice.onNext(connectedDevice)
                    }
                }
    }

    private val scoAudioState : Observable<Int> by lazy {
        BehaviorSubject.create<Int>().apply {
            appContext.receiveBroadcasts(false, AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                    .subscribeSimple {
                        val newAudioState = it.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                        logd("SCO: New audio state: $newAudioState")
                        this.onNext(newAudioState)
                    }
        }
    }

    private var scoSubscription : Subscription? = null

    private fun startSco() {
        scoSubscription?.unsubscribe()
        scoSubscription = scoAudioState
                // 如果我们刚好正在连接的过程, 要等这个过程完了才进行下一步
                .first { it != AudioManager.SCO_AUDIO_STATE_CONNECTING }
                .flatMap {
                    // 请求开启SCO
                    logd("SCO: audioManager.startBluetoothSco()")
                    audioManager.mode = AudioManager.MODE_NORMAL
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    audioManager.mode = AudioManager.MODE_IN_CALL

                    if (it == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                        // 如果现在SCO已经连接, 那么不需要等待什么
                        Unit.toObservable()
                    }
                    else {
                        // 如果现在SCO状态不为已经连接, 等待连接状态变为连接, 并有2秒的超时时间.
                        scoAudioState.map {
                            if (it == AudioManager.SCO_AUDIO_STATE_ERROR) {
                                throw RuntimeException("SCO connection error")
                            }
                            else {
                                it
                            }
                        }.first { it == AudioManager.SCO_AUDIO_STATE_CONNECTED }
                                .timeout(2, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                    }
                }
                .doOnError {
                    logd("SCO: Error: $it")
                    // 出现错误要停掉SCO
                    audioManager.stopBluetoothSco()
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
                .retry { count : Int, throwable : Throwable  ->
                    (throwable is TimeoutException).apply {
                        if (this && count <= BT_RETRY_COUNT) {
                            logd("SCO: Retrying starting sco...")
                        }
                    }
                }
                .onErrorReturn { Unit }
                .subscribeSimple()
    }

    private fun stopSco() {
        scoSubscription?.unsubscribe()
        scoSubscription = scoAudioState
                // 如果我们刚好正在连接的过程, 要等这个过程完了才进行下一步
                .first { it != AudioManager.SCO_AUDIO_STATE_CONNECTING }
                .map {
                    if (it == AudioManager.SCO_AUDIO_STATE_CONNECTED || it == AudioManager.SCO_AUDIO_STATE_ERROR) {
                        // 请求关闭SCO
                        logd("SCO: audioManager.stopBluetoothSco()")
                        audioManager.stopBluetoothSco()
                        audioManager.mode = AudioManager.MODE_NORMAL
                    }
                    else {
                        Unit.toObservable()
                    }
                }
                .subscribeSimple()
    }


    private fun retrieveMediaButtonEvent() : Observable<Intent> {
        return Observable.create<Intent> { subscriber ->
            val componentName = ComponentName(appContext, RemoteControlClientReceive::class.java.name)
            audioManager.registerMediaButtonEventReceiver(componentName)

            subscriber.add(Subscriptions.create {
                audioManager.unregisterMediaButtonEventReceiver(componentName)
            })
        }.subscribeOnMainThread()
    }

    class RemoteControlClientReceive : BroadcastReceiver() {
        override fun onReceive(content: Context, intent: Intent) {
        }
    }

    /**
     * 查找已连接的手咪设备
     */
    private fun queryBluetoothDevice(btAdapter : BluetoothAdapter) : Observable<List<BluetoothDevice>> {
        return appContext.receiveBroadcasts(false, BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothAdapter.ACTION_STATE_CHANGED)
                .debounce(1, TimeUnit.SECONDS)
                .startWith(null as Intent?)
                .flatMap {
                    logd("QUERY: Querying connected devices...")
                    if (btAdapter.isEnabled.not()) {
                        emptySet<BluetoothDevice>().toObservable()
                    }
                    else {
                        getBluetoothProfileConnectedDevices(btAdapter, BluetoothProfile.HEADSET)
                    }
                }
                .doOnNext { logd("QUERY: Got connected bluetooth devices: ${it.joinToString(",", transform = { it.name })}") }
                .map { it.filter { it.name.contains("PTT") } }
    }

    private fun getBluetoothProfileConnectedDevices(btAdapter: BluetoothAdapter, profileRequested: Int) : Observable<Set<BluetoothDevice>> {
        return Observable.create { subscriber ->
            btAdapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceDisconnected(profile: Int) { }

                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    subscriber.onSingleValue(proxy?.connectedDevices?.toSet() ?: emptySet())
                    btAdapter.closeProfileProxy(profile, proxy)
                }
            }, profileRequested)
        }
    }
}