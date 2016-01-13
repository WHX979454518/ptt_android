package com.xianzhitech.ptt.engine;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.media.AudioManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

import rx.Observable;
import rx.Subscriber;

/**
 * Created by suke on 16/1/12.
 */
public class BtEngine {
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Context context;
    private BluetoothAdapter btAdapter;
    private BluetoothDevice selectedDevice = null;
    private BluetoothSocket socket = null;
    private boolean isShutdown = false;


    public BtEngine(Context context) {
        this.context = context;
        init();
    }

    private void init() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter == null) {
            System.out.println("not support Bluetooth");
            return;
        }

        if (!btAdapter.isEnabled()) {
            //TODO: enable bluetooth
            //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //((Activity) this.context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    /**
     * PTT 设备是否可用，因为可能会有一个复杂的开启和关闭过程，所以用Observable
     *
     * @return
     */
    public Observable<Boolean> getBtMicEnable() {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {

                boolean enable = false;

                if (btAdapter.isEnabled()) {

                    boolean sco = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE))
                            .isBluetoothScoAvailableOffCall();

                    if (sco) {

                        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

                        // If there are paired devices
                        if (pairedDevices.size() > 0) {

                            // Loop through paired devices
                            for (BluetoothDevice device : pairedDevices) {

                                // Add the name and address to an array adapter to show in a ListView
                                System.out.println(device.getName() + "\n" + device.getAddress());
                                if (device.getName().contains("PTT")) {
                                    selectedDevice = device;
                                    break;
                                }
                            }
                        }

                        if (selectedDevice != null) {
                            enable = true;
                        }
                    }
                }

                //返回是否可用蓝牙MIC
                if (!subscriber.isUnsubscribed()) {
                    subscriber.onNext(enable);
                    subscriber.onCompleted();
                }
            }
        });
    }

    //停止
    public void shutdown() {
        isShutdown = true;

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = null;
        }
    }

    /**
     * 取得BT Mic发出的指令
     *
     * @return
     */
    public Observable<String> getBtMessage() {
        return Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {

                if (btAdapter == null || !btAdapter.isEnabled()) {
                    if (!subscriber.isUnsubscribed())
                        subscriber.onError(new Exception("bluetooth unsupported"));
                    return;
                }


                if (selectedDevice == null) {
                    if (!subscriber.isUnsubscribed())
                        subscriber.onError(new Exception("not found ptt device"));
                    return;
                }

                // Get a BluetoothSocket to connect with the given BluetoothDevice
                try {
                    // MY_UUID is the app's UUID string, also used by the server code
                    socket = selectedDevice.createRfcommSocketToServiceRecord(BT_UUID);
                } catch (IOException e) {
                    if (!subscriber.isUnsubscribed())
                        subscriber.onError(e);
                    e.printStackTrace();
                    return;
                }

                // Cancel discovery because it will slow down the connection
                btAdapter.cancelDiscovery();

                try {
                    // Connect the device through the socket. This will block
                    // until it succeeds or throws an exception
                    socket.connect();

                    InputStream ins = socket.getInputStream();

                    byte[] buf = new byte[100];
                    int count = 0;
                    while ((count = ins.read(buf)) != -1) {
                        //发送事件
                        if (!subscriber.isUnsubscribed())
                            subscriber.onNext(new String(buf, 0, count));
                    }

                } catch (IOException connectException) {
                    // Unable to connect; close the socket and get out
                    try {
                        socket.close();
                    } catch (IOException closeException) {
                    }

                    if (isShutdown) {
                        if (!subscriber.isUnsubscribed())
                            subscriber.onCompleted();
                    } else {
                        if (!subscriber.isUnsubscribed())
                            subscriber.onError(connectException);
                    }
                }
            }
        });
    }
}
