package com.xianzhitech.ptt.engine

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.xianzhitech.ptt.ext.GlobalSubscriber
import com.xianzhitech.ptt.ext.logd
import com.xianzhitech.ptt.ext.receiveBroadcasts
import com.xianzhitech.ptt.ext.subscribeOnMainThread
import rx.Observable
import rx.schedulers.Schedulers
import rx.subscriptions.Subscriptions
import java.util.*
import java.util.concurrent.Executors


interface NewBtEngine {
    companion object {
        const val MESSAGE_DEV_PTT_OK = "DEV_PTT_OK"
        const val MESSAGE_PUSH_DOWN = "+PTT=P"
        const val MESSAGE_PUSH_RELEASE = "+PTT=R"
    }

    /**
     * 连接到手咪蓝牙设备, 并接收控制信号.
     * 当这个订阅结束时, 蓝牙设备将被关闭.
     *
     * @exception UnsupportedOperationException 当蓝牙设备不可用时, 将抛出此异常
     */
    fun receiveCommand() : Observable<String>
}


class NewBtEngineImpl(private val context: Context) : NewBtEngine {
    companion object {
        private val BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val audioManager : AudioManager

    // 所有蓝牙操作都在此executor, scheduler上进行, 以防止线程问题
    private val bluetoothExecutor = Executors.newSingleThreadExecutor()
    private val bluetoothScheduler = Schedulers.from(bluetoothExecutor)


    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun receiveCommand(): Observable<String> {
        return BluetoothAdapter.getDefaultAdapter()?.let { btAdapter ->
            queryBluetoothDevice(btAdapter)
                    .first() // 只关心第一个找到的设备
                    .flatMap { bluetoothDevice ->
                        logd("Got bluetooth device: {name: ${bluetoothDevice.name}, addr: ${bluetoothDevice.address}")

                        Observable.create<String> { subscriber ->
                            // 1. 发送该设备已激活的消息
                            subscriber.onNext(NewBtEngine.MESSAGE_DEV_PTT_OK)

                            // 2. 绑定媒体按键事件
                            subscriber.add(retrieveMediaButtonEvent().subscribe(GlobalSubscriber<Intent>()))

                            // 3. 绑定处理SCO音频信息更新的广播事件. 在收到连接状态时打开音频管理器的SCO
                            subscriber.add(context.receiveBroadcasts(false, AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED).subscribe {
                                logd("SCO AUDIO STATE UPDATED: $it")
                                if (it.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                                    audioManager.isBluetoothScoOn = true
                                }
                            })

                            // 4. 开启外围蓝牙扬声器
                            audioManager.stopBluetoothSco()
                            audioManager.startBluetoothSco()

                            try {
                                // 5. 连接到蓝牙socket
                                logd("Connecting to bluetooth socket")
                                bluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID)?.use { socket ->

                                    // 取消蓝牙发现以便加速连接
                                    btAdapter.cancelDiscovery()

                                    socket.connect()
                                    logd("Connected to bluetooth socket")
                                    socket.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                                        val allCommands = listOf(NewBtEngine.MESSAGE_PUSH_DOWN, NewBtEngine.MESSAGE_PUSH_RELEASE)
                                        val matchedCommands = allCommands.toMutableList()
                                        var matchedSize = 0

                                        // 一直死循环读取数据直到取消订阅为止
                                        while (subscriber.isUnsubscribed.not()) {
                                            val char : Char = reader.read().let {
                                                if (it < 0) {
                                                    // -1表明这个流已经读完了. 在实际中, 这个属于一个异常, 因为我们总期待蓝牙一直连接
                                                    throw InterruptedException("Bluetooth stream ended")
                                                }
                                                else {
                                                    it.toChar()
                                                }
                                            }

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
                            }
                            catch (throwable : Throwable) {
                                subscriber.onError(throwable)
                            }
                            finally {
                                audioManager.isBluetoothScoOn = false
                                audioManager.stopBluetoothSco()
                            }
                        }.subscribeOn(bluetoothScheduler)
                    }
        } ?: Observable.error<String>(UnsupportedOperationException())
    }

    /**
     * 查找已连接的手咪设备
     */
    internal fun queryBluetoothDevice(btAdapter : BluetoothAdapter) : Observable<BluetoothDevice> {
        if (audioManager.isBluetoothScoAvailableOffCall.not()) {
            return Observable.error(UnsupportedOperationException())
        }

        return context.receiveBroadcasts(false, BluetoothDevice.ACTION_ACL_CONNECTED)
                .map { it.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) }
                .mergeWith(getBluetoothProfileConnectedDevices(btAdapter, BluetoothProfile.A2DP))
                .mergeWith(getBluetoothProfileConnectedDevices(btAdapter, BluetoothProfile.HEADSET))
                .doOnNext { logd("Checking for bluetooth device ${it?.name} : ${it?.address}") }
                .filter { it != null && it.name.contains("PTT") }
    }

    internal fun getBluetoothProfileConnectedDevices(btAdapter: BluetoothAdapter, profileRequested: Int) : Observable<BluetoothDevice> {
        return Observable.create { subscriber ->
            btAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceDisconnected(profile: Int) {
                }

                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == profileRequested) {
                        proxy.connectedDevices?.forEach { subscriber.onNext(it) }
                    }
                    subscriber.onCompleted()
                    btAdapter.closeProfileProxy(profile, proxy)
                }
            }, profileRequested)
        }
    }

    private fun retrieveMediaButtonEvent() : Observable<Intent> {
        return Observable.create<Intent> { subscriber ->
            val componentName = ComponentName(context, RemoteControlClientReceive::class.java.name)
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

}

