package com.xianzhitech.ptt.engine;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * Created by suke on 16/1/12.
 */
public class BtEngine {
    public static final String MESSAGE_PUSH_DOWN = "+PTT=P";
    public static final String MESSAGE_PUSH_RELEASE = "+PTT=R";

    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Context context;
    private
    @Nullable
    BluetoothAdapter btAdapter;
    private AudioManager audioManager;
    private BluetoothDevice selectedDevice = null;
    private BluetoothSocket socket = null;
    private boolean isShutdown = false;
    private BroadcastReceiver broadcastReceiver;

    private ComponentName mRemoteControlClientReceiverComponent;//接管MediaButton

    class RemoteControlClientReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equalsIgnoreCase(Intent.ACTION_MEDIA_BUTTON)) {
                System.out.println("RemoteControlClientReceiver = " + intent);
            }

        }
    }


    public BtEngine(Context context) {
        this.context = context;
        init();
    }

    private void init() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mRemoteControlClientReceiverComponent = new ComponentName(context.getPackageName(), RemoteControlClientReceiver.class.getName());

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
     * PTT 设备是否可用
     *
     * @return
     */
    public boolean isBtMicEnable() {
        if (btAdapter != null && btAdapter.isEnabled() && audioManager.isBluetoothScoAvailableOffCall()) {
            // Loop through paired devices
            for (final BluetoothDevice device : btAdapter.getBondedDevices()) {
                // Add the name and address to an array adapter to show in a ListView
                System.out.println(device.getName() + "\n" + device.getAddress());
                if (device.getName().contains("PTT")) {
                    selectedDevice = device;
                    return true;
                }
            }
        }

        return false;
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
                    subscriber.onError(new Exception("bluetooth unsupported"));
                    return;
                }


                if (selectedDevice == null) {
                    subscriber.onError(new Exception("not found ptt device"));
                    return;
                }

                // Get a BluetoothSocket to connect with the given BluetoothDevice
                try {
                    // MY_UUID is the app's UUID string, also used by the server code
                    socket = selectedDevice.createRfcommSocketToServiceRecord(BT_UUID);
                } catch (IOException e) {
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
                    int count;
                    while ((count = ins.read(buf)) != -1 && !subscriber.isUnsubscribed()) {
                        //发送事件
                        subscriber.onNext(new String(buf, 0, count));
                    }

                } catch (IOException connectException) {
                    // Unable to connect; close the socket and get out
                    try {
                        socket.close();
                    } catch (IOException closeException) {
                    }

                    if (isShutdown) {
                        subscriber.onCompleted();
                    } else {
                        subscriber.onError(connectException);
                    }
                }
            }
        }).subscribeOn(Schedulers.io());
    }


    //请求启用sco
    public void startSCO() {
        try {

            audioManager.registerMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);

            audioManager.stopBluetoothSco();
            //这儿主要是想开启外置蓝牙扬声器。
            audioManager.startBluetoothSco();

            System.out.println("startSCO >>>>>>>>>>>>>>>>");

            broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);

                    System.out.println("UPDATED : code = " + state + " intent = " + intent);


                    if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {

                        System.out.println("AudioManager.SCO_AUDIO_STATE_CONNECTED");
                        audioManager.setBluetoothScoOn(true);
                    }
                }
            };
            this.context.registerReceiver(broadcastReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //停用sco
    public void stopSCO() {
        try {
            audioManager.stopBluetoothSco();

            audioManager.unregisterMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (broadcastReceiver != null) {
            context.unregisterReceiver(broadcastReceiver);
            broadcastReceiver = null;
        }
    }
}
