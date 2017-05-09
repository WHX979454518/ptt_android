package com.zrt.ptt.app.console.mvp.view.fragment;

import android.content.Context;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.baidu.MyOrientationListener;
import com.zrt.ptt.app.console.mvp.view.IView.IConsoMapView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;


public class ConsoleMapFragment extends Fragment implements IConsoMapView {

    @BindView(R.id.bmapView)
    MapView bmapView;
    @BindView(R.id.organiz_function_inc)
    LinearLayout organizFunctionInc;
    @BindView(R.id.sys_state_func_inc)
    LinearLayout sysStateFuncInc;
    @BindView(R.id.organiz_func_container)
    FrameLayout organizFuncContainer;
    Unbinder unbinder;
    public LocationClient mLocationClient = null;
    /**
     * 当前定位的模式
     */
    private MyLocationConfiguration.LocationMode mCurrentMode = MyLocationConfiguration.LocationMode.NORMAL;
    /**
     * 方向传感器X方向的值
     */
    private int mXDirection;
    /**
     * 方向传感器的监听器
     */
    private MyOrientationListener myOrientationListener;
    /***
     * 是否是第一次定位
     */

    /**
     * 最新一次的经纬度
     */
    private double mCurrentLantitude = 30.667437;
    private double mCurrentLongitude = 104.066832;
    /**
     * 当前的精度
     */
    private float mCurrentAccracy;
    private volatile boolean isFristLocation = true;
    public BDLocationListener myListener ;
    //定位都要通过LocationManager这个类实现
    private LocationManager locationManager;
    private String provider;
    BaiduMap baiduMap;
    boolean ifFrist = true;

    public ConsoleMapFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_console_map, container, false);
        unbinder = ButterKnife.bind(this, view);
        initView();
        return view;
    }


    private void initView()
    {
        baiduMap = bmapView.getMap();
        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(15.0f);
        baiduMap.setMapStatus(msu);
        initMyLocation();
//        initOritationListener();
    }



    /**
     * 初始化定位相关代码
     */
    private void initMyLocation() {
        // 定位初始化
        mLocationClient = new LocationClient(getActivity());
        myListener = new MyLocationListener();
        mLocationClient.registerLocationListener(myListener);
        // 设置定位的相关配置
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true);// 打开gps
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(1000);
        mLocationClient.setLocOption(option);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        bmapView.onDestroy();
        baiduMap.setMyLocationEnabled(false);
    }

    @Override
    public void onStart() {
        // 开启图层定位
        baiduMap.setMyLocationEnabled(true);
        if (!mLocationClient.isStarted()) {
            mLocationClient.start();
        }
        // 开启方向传感器
//        myOrientationListener.start();
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        bmapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        bmapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        // 关闭图层定位
        baiduMap.setMyLocationEnabled(false);
        mLocationClient.stop();

        // 关闭方向传感器
//        myOrientationListener.stop();
        super.onStop();
    }

    //展示传递过来的用户参数坐标定位
    @Override
    public void showUsersLocation(List<LatLng> locations) {
        baiduMap.clear();
        LatLng latLng = null;
        Marker marker = null;
        BitmapDescriptor mMarker = BitmapDescriptorFactory.fromResource(R.drawable.icon_marka);
        OverlayOptions options;
        for (LatLng latLin : locations)
        {
            // 经纬度
            latLng = new LatLng(latLin.latitude, latLin.longitude);
            // 图标
            options = new MarkerOptions().position(latLng).icon(mMarker)
                    .zIndex(5);
            marker = (Marker) baiduMap.addOverlay(options);
            //屏蔽代码属于描述maker展示的详情
            /*Bundle arg0 = new Bundle();
            arg0.putSerializable("info", latLin);
            marker.setExtraInfo(arg0);*/
        }
//        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(15.0f);
        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(latLng);
        baiduMap.setMaxAndMinZoomLevel(21.0f,15.0f);
        baiduMap.setMapStatus(msu);
        msu = MapStatusUpdateFactory.zoomTo(20.0f);
        baiduMap.setMapStatus(msu);
    }


    public class MyLocationListener implements BDLocationListener {

        @Override
        public void onReceiveLocation(BDLocation location) {

            // map view 销毁后不在处理新接收的位置
            if (location == null || bmapView == null)
                return;
       /* // 构造定位数据
       MyLocationData data = new MyLocationData.Builder()//
					.direction(mCurrentX)//
					.accuracy(location.getRadius())//
					.latitude(location.getLatitude())//
					.longitude(location.getLongitude())//
					.build();*/
       //西宁101.764866,36.640738
            // 构造定位数据假数据上线再去掉
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(mXDirection).latitude(36.640738)
                    .longitude(101.764866).build();
            mCurrentAccracy = location.getRadius();
            // 设置定位数据
            baiduMap.setMyLocationData(locData);
//        mCurrentLantitude = location.getLatitude();
//        mCurrentLongitude = location.getLongitude();
            mCurrentLantitude = 30.667437;
            mCurrentLongitude = 104.066832;
            // 设置自定义图标
            BitmapDescriptor mCurrentMarker = BitmapDescriptorFactory
                    .fromResource(R.drawable.icon_marka);
            MyLocationConfiguration config = new MyLocationConfiguration(
                    mCurrentMode, true, null);
            baiduMap.setMyLocationConfigeration(config);
            // 第一次定位时，将地图位置移动到当前位置
            if (isFristLocation)
            {
                LatLng ll = new LatLng(36.640738,
                        101.764866);
                MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
                baiduMap.animateMapStatus(u);
                isFristLocation = false;
            }
        }
    }

    /**
     * 初始化方向传感器
     */
    private void initOritationListener()
    {
        myOrientationListener = new MyOrientationListener(
                App.getInstance().getApplicationContext());
        myOrientationListener
                .setOnOrientationListener(new MyOrientationListener.OnOrientationListener()
                {
                    @Override
                    public void onOrientationChanged(float x)
                    {
                        mXDirection = (int) x;

                        // 构造定位数据
                        MyLocationData locData = new MyLocationData.Builder()
                                .accuracy(mCurrentAccracy)
                                // 此处设置开发者获取到的方向信息，顺时针0-360
                                .direction(mXDirection)
                                .latitude(mCurrentLantitude)
                                .longitude(mCurrentLongitude).build();
                        // 设置定位数据
                        baiduMap.setMyLocationData(locData);
                        // 设置自定义图标
                        BitmapDescriptor mCurrentMarker = BitmapDescriptorFactory
                                .fromResource(R.drawable.icon_marka);
                        MyLocationConfiguration config = new MyLocationConfiguration(
                                mCurrentMode, true, mCurrentMarker);
                        baiduMap.setMyLocationConfigeration(config);

                    }
                });
    }
}
