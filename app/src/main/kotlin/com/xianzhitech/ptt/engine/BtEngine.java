package com.xianzhitech.ptt.engine;

import android.accounts.OperationCanceledException;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * Created by suke on 16/1/12.
 */
public class BtEngine {
    public static final String MESSAGE_DEV_PTT_OK = "DEV_PTT_OK";
    public static final String MESSAGE_PUSH_DOWN = "+PTT=P";
    public static final String MESSAGE_PUSH_RELEASE = "+PTT=R";

    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Context context;
    private
    @Nullable
    BluetoothAdapter btAdapter = null;
    private AudioManager audioManager;
    private BluetoothDevice selectedDevice = null;
    private BluetoothSocket socket = null;
    private BroadcastReceiver broadcastReceiver = null;
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
//        btAdapter = BluetoothAdapter.getDefaultAdapter();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
//        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI);
        mRemoteControlClientReceiverComponent = new ComponentName(context.getPackageName(), RemoteControlClientReceiver.class.getName());
    }

    //间隔300毫秒检查一次有否可用的手咪设备，直到订阅取消或者有可用的设备
    private void getDev(Subscriber<? super String> subscriber) {
        while (!subscriber.isUnsubscribed()) {
            if (btAdapter == null) {
                System.out.println("not support Bluetooth");
                throw new UnsupportedOperationException("not support Bluetooth");
            }
            if (BluetoothProfile.STATE_CONNECTED != btAdapter.getProfileConnectionState(BluetoothProfile.A2DP) ||
                    BluetoothProfile.STATE_CONNECTED != btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET))
            {//没有已连接的蓝牙设备
                sleep(300);
                continue;
            }

            if (!audioManager.isBluetoothScoAvailableOffCall())
            {
                sleep(300);
                continue;
            }

            // Loop through paired devices
            for (final BluetoothDevice device : btAdapter.getBondedDevices()) {
                // Add the name and address to an array adapter to show in a ListView
                if (device.getName().contains("PTT")) {
                    selectedDevice = device;
                    break;
                }
            }
            if(selectedDevice == null)
            {
                sleep(300);
                continue;
            }
            break;
        }
    }

    //如果有可用的设备返回true，没有返回false,同时启动监听手咪直到可用或者stopSCO被调用
    public Observable<String> startSCO(){
        return Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                btAdapter = BluetoothAdapter.getDefaultAdapter();
                //1.获取可用设备后发送：MESSAGE_DEV_PTT_OK
                try {
                    getDev(subscriber);
                }catch (UnsupportedOperationException uoex)
                {
                    subscriber.onError(uoex);
                    return;
                }
                if (subscriber.isUnsubscribed())
                {
                    return;
                }
                subscriber.onNext(BtEngine.MESSAGE_DEV_PTT_OK);
                // Get a BluetoothSocket to connect with the given BluetoothDevice
                try {
                    //2.使用设备
                    startSCO_();
                    // MY_UUID is the app's UUID string, also used by the server code
                    socket = selectedDevice.createRfcommSocketToServiceRecord(BT_UUID);
                } catch (IOException e) {
                    subscriber.onError(e);
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
                    while (!subscriber.isUnsubscribed()) {
                        if((count = ins.read(buf)) != -1) { //发送事件
                            subscriber.onNext(new String(buf, 0, count));
                            Log.i("PTT",new String(buf, 0, count));
                        }else
                        {
                            Log.i("PTT","ins quite");
                        }
                    }
                } catch (IOException connectException) {
                    // Unable to connect; close the socket and get out
                    subscriber.onError(connectException);
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    private void sleep(long time)
    {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    //请求启用sco
    public void startSCO_() {
        audioManager.registerMediaButtonEventReceiver(mRemoteControlClientReceiverComponent);
        audioManager.stopBluetoothSco();
        //这儿主要是想开启外置蓝牙扬声器。
        audioManager.startBluetoothSco();
            System.out.println("startSCO >>>>>>>>>>>>>>>>");
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
//                    System.out.println("UPDATED : code = " + state + " intent = " + intent);
                if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
//                        System.out.println("AudioManager.SCO_AUDIO_STATE_CONNECTED");
                    audioManager.setBluetoothScoOn(true);
                }
            }
        };
        this.context.registerReceiver(broadcastReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
    }

    //停用sco
    public void stopSCO() {
        try {
            socket.close();
            selectedDevice = null;
            btAdapter = null;
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
