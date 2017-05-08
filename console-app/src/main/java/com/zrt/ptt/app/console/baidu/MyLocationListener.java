package com.zrt.ptt.app.console.baidu;

import android.util.Log;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.Poi;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.zrt.ptt.app.console.R;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationConfiguration.LocationMode;
import com.baidu.mapapi.map.MyLocationData;

import java.util.List;

/**
 * Created by ASUS on 2017/5/7.
 */

public class MyLocationListener implements BDLocationListener {
    /**
     * 当前定位的模式
     */
    private LocationMode mCurrentMode = LocationMode.NORMAL;
    /***
     * 是否是第一次定位
     */
    private volatile boolean isFristLocation = true;

    /**
     * 最新一次的经纬度
     */
    private double mCurrentLantitude;
    private double mCurrentLongitude;
    /**
     * 当前的精度
     */
    private float mCurrentAccracy;
    /**
     * 方向传感器的监听器
     */
    private MyOrientationListener myOrientationListener;
    /**
     * 方向传感器X方向的值
     */
    private int mXDirection;
    private MapView mMapView;
    BaiduMap baiduMap;

    public MyLocationListener(MapView mMapView, BaiduMap baiduMap,boolean isFristLocation) {
        this.mMapView = mMapView;
        this.baiduMap = baiduMap;
        this.isFristLocation = isFristLocation;
    }

    @Override
    public void onReceiveLocation(BDLocation location) {

        // map view 销毁后不在处理新接收的位置
        if (location == null || mMapView == null)
            return;
        // 构造定位数据
        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(location.getRadius())
                // 此处设置开发者获取到的方向信息，顺时针0-360
                .direction(mXDirection).latitude(location.getLatitude())
                .longitude(location.getLongitude()).build();
        mCurrentAccracy = location.getRadius();
        // 设置定位数据
        baiduMap.setMyLocationData(locData);
//        mCurrentLantitude = location.getLatitude();
        mCurrentLantitude = 104.072218;
//        mCurrentLongitude = location.getLongitude();
        mCurrentLongitude = 30.663468;
        // 设置自定义图标
        BitmapDescriptor mCurrentMarker = BitmapDescriptorFactory
                .fromResource(R.drawable.navi_map_gps_locked);
        MyLocationConfiguration config = new MyLocationConfiguration(
                mCurrentMode, true, mCurrentMarker);
        baiduMap.setMyLocationConfigeration(config);
        // 第一次定位时，将地图位置移动到当前位置
        if (isFristLocation)
        {
            isFristLocation = false;
            LatLng ll = new LatLng(104.072218,
                    30.663468);
            MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
            baiduMap.animateMapStatus(u);
        }
    }
}

