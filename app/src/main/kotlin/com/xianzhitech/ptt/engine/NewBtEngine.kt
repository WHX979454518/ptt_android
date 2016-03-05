package com.xianzhitech.ptt.engine

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.support.v4.media.session.MediaSessionCompat
import com.xianzhitech.ptt.MediaButtonEventReceiver
import com.xianzhitech.ptt.ext.receiveBroadcasts
import rx.Observable
import rx.functions.Func1
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
                        Observable.create<String> { subscriber ->
                            // 1. 发送该设备已激活的消息
                            subscriber.onNext(NewBtEngine.MESSAGE_DEV_PTT_OK)

                            // 2. 绑定媒体按键事件
                            subscriber.add(retrieveMediaButtonEvent().subscribe {
                                //TODO: 处理媒体按键事件
                            })

                            // 3. 绑定处理SCO音频信息更新的广播事件. 在收到连接状态时打开音频管理器的SCO
                            subscriber.add(context.receiveBroadcasts(false, AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED).subscribe {
                                if (it.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                                    audioManager.isBluetoothScoOn = true
                                }
                            })

                            // 4. 开启外围蓝牙扬声器
                            audioManager.stopBluetoothSco()
                            audioManager.startBluetoothSco()

                            try {
                                // 5. 连接到蓝牙socket
                                bluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID)?.use { socket ->

                                    // 取消蓝牙发现以便加速连接
                                    btAdapter.cancelDiscovery()

                                    socket.connect()
                                    socket.inputStream.use { stream ->
                                        val buffer = ByteArray(100)

                                        // 一直死循环读取数据直到取消订阅为止
                                        while (subscriber.isUnsubscribed.not()) {
                                            val readCount = stream.read(buffer)

                                            if (readCount > 0) {
                                                //TODO: 是否可能有两个很快连续的命令过来?
                                                subscriber.onNext(String(buffer, 0, readCount))
                                            }
                                            else if (readCount < 0) {
                                                // 这个流已经读完了. 在实际中, 这个属于一个异常, 因为我们总期待蓝牙一直连接
                                                throw InterruptedException("Bluetooth stream interrupted")
                                            }
                                        }
                                    }
                                }
                            }
                            catch (throwable : Throwable) {
                                subscriber.onError(throwable)
                            }
                            finally {
                                audioManager.stopBluetoothSco()
                            }

                        }
                    }


                    .subscribeOn(bluetoothScheduler)
        } ?: Observable.error<String>(UnsupportedOperationException())
    }

    /**
     * 查找已配对的手咪设备
     */
    internal fun queryBluetoothDevice(btAdapter : BluetoothAdapter) : Observable<BluetoothDevice> {
        val query = Func1<Any?, BluetoothDevice?> {
            if (BluetoothProfile.STATE_CONNECTED != btAdapter.getProfileConnectionState(BluetoothProfile.A2DP) ||
                    BluetoothProfile.STATE_CONNECTED != btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) ||
                    audioManager.isBluetoothScoAvailableOffCall.not()) {
                null
            } else {
                btAdapter.bondedDevices?.firstOrNull { it.name.contains("PTT") }
            }
        }

        return (context.receiveBroadcasts(false, BluetoothDevice.ACTION_ACL_CONNECTED)
                .map(query)
                .startWith(query.call(null))
                .filter { it != null })
                as Observable<BluetoothDevice>
    }

    private fun retrieveMediaButtonEvent() : Observable<Intent> {
        return Observable.create { subscriber ->
            val mediaSession = MediaSessionCompat(context, "BtEngine", ComponentName(context, MediaButtonEventReceiver::class.java), null)

            mediaSession.setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    subscriber.onNext(mediaButtonEvent)
                    return true
                }
            })

            subscriber.add(Subscriptions.create {
                mediaSession.release()
            })
        }
    }

}

