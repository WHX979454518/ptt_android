package com.zrt.ptt.app.console.mvp.view.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.xianzhitech.ptt.ui.base.BaseActivity;
import com.xianzhitech.ptt.ui.roomlist.RoomListFragment;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.baidu.MyLocationListener;
import com.zrt.ptt.app.console.mvp.presenter.MainActivityPresenter;
import com.zrt.ptt.app.console.mvp.view.IView.IMainActivityView;
import com.zrt.ptt.app.console.mvp.view.fragment.OrganizationFragment;
import com.zrt.ptt.app.console.mvp.view.fragment.SystemStateFragment;

import org.json.JSONObject;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends BaseActivity implements View.OnClickListener, IMainActivityView {
    @BindView(R.id.pupmenu)
    ImageView pupmenu;
    @BindView(R.id.all_call)
    TextView allCall;
    @BindView(R.id.sign_out)
    LinearLayout signOut;
    @BindView(R.id.time_hms)
    TextView timeHms;
    @BindView(R.id.console)
    LinearLayout console;
    @BindView(R.id.time_ymd)
    TextView timeYmd;
    @BindView(R.id.logo)
    ImageView logo;
    @BindView(R.id.main_title)
    LinearLayout mainTitle;
   /* @BindView(R.id.rb1)
    RadioButton rb1;
    @BindView(R.id.rb2)
    RadioButton rb2;
    @BindView(R.id.rb3)
    RadioButton rb3;*/
    @BindView(R.id.contacts_container)
    LinearLayout contactsContainer;
    @BindView(R.id.bmapView)
    MapView bmapView;
    @BindView(R.id.map_container)
    FrameLayout mapContainer;
    @BindView(R.id.main_content)
    LinearLayout mainContent;
    @BindView(R.id.organiz_func_container)
    FrameLayout organizFuncContainer;
    @BindView(R.id.organiz_function_inc)
    LinearLayout organizFunctionInc;
    @BindView(R.id.sys_state_func_inc)
    LinearLayout sysStateFuncInc;
    private MapView mMapView;
    private OrganizationFragment organizationFragment;
    private SystemStateFragment stateFragment;
    private RoomListFragment roomListFragment;
    private PopupWindow popupWindow;
    private View rootView;
    private LinearLayout logRecord;
    private LinearLayout playBack;
    private ImageView userLocation;
    private MainActivityPresenter mainPresenter = new MainActivityPresenter(this);
    public LocationClient mLocationClient = null;
    public BDLocationListener myListener = new MyLocationListener();
    //定位都要通过LocationManager这个类实现
    private LocationManager locationManager;
    private String provider;
    BaiduMap baiduMap;
    boolean ifFrist = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//remove title bar 即隐藏标题栏
        getSupportActionBar().hide();// 隐藏ActionBar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//remove notification bar 即全屏
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        initMapViewLocation();
        initView();
        mLocationClient = new LocationClient(getApplicationContext());
        //声明LocationClient类
        mLocationClient.registerLocationListener(myListener);
        //注册监听函数
        initLocation();
        mLocationClient.start();
//        mainPresenter.UpDataOrganzation();
    }

    private void initView() {
        View view = getLayoutInflater().inflate(R.layout.organiz_function_btn_ly, null);
        rootView = findViewById(R.id.main_title);
        View popupView = getLayoutInflater().inflate(R.layout.menu_popup, null);
        logRecord = (LinearLayout) popupView.findViewById(R.id.log_record);
        logRecord.setOnClickListener(this);

        playBack = (LinearLayout) popupView.findViewById(R.id.play_back);
        playBack.setOnClickListener(this);
        popupWindow = new PopupWindow(popupView, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, true);
//        popupWindow.setContentView(popupView);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new BitmapDrawable());
        popupWindow.setFocusable(true);
        popupWindow.setTouchable(true);
        initFragment();

        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                // popupWindow隐藏时恢复屏幕正常透明度
//                setBackgroundAlpha(1.0f);
            }
        });
        popupWindow.setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    popupWindow.dismiss();
                    return true;
                }
                return false;
            }
        });
    }

    public void setBackgroundAlpha(float bgAlpha) {
        WindowManager.LayoutParams lp = getWindow()
                .getAttributes();
        lp.alpha = bgAlpha;
        getWindow().setAttributes(lp);
    }

    /**
     * 切换左侧Fragment
     *
     *
     */
    public void initFragment() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
                if (organizationFragment == null) {
                    organizationFragment = new OrganizationFragment();
                    ft.add(R.id.contacts_container, organizationFragment);
                } else
                    ft.show(organizationFragment);

                if (stateFragment == null) {
                    stateFragment = new SystemStateFragment();
                    ft.add(R.id.system_state_container, stateFragment);
                } else
                    ft.show(stateFragment);

                /*if (roomListFragment == null) {
                    roomListFragment = new RoomListFragment();
                    ft.add(R.id.contacts_container, roomListFragment);
                } else if (!roomListFragment.isVisible())
                    ft.show(roomListFragment);*/

        ft.commitAllowingStateLoss();
    }


   /* public void checkRadio(int id) {
        switch (id) {
            case R.id.rb1:
                rb1.setChecked(true);
                rb1.setBackgroundColor(getResources().getColor(R.color.btn_pre_red));
                rb2.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb3.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                organizFunctionInc.setVisibility(View.VISIBLE);
                sysStateFuncInc.setVisibility(View.GONE);
                rb2.setChecked(false);
                rb3.setChecked(false);
                break;
            case R.id.rb2:
                rb2.setChecked(true);
                rb1.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb2.setBackgroundColor(getResources().getColor(R.color.btn_pre_red));
                rb3.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                organizFunctionInc.setVisibility(View.GONE);
                sysStateFuncInc.setVisibility(View.VISIBLE);
                rb1.setChecked(false);
                rb3.setChecked(false);
                break;
            case R.id.rb3:
                rb3.setChecked(true);
                rb1.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb2.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb3.setBackgroundColor(getResources().getColor(R.color.btn_pre_red));
                organizFunctionInc.setVisibility(View.GONE);
                sysStateFuncInc.setVisibility(View.GONE);
                rb1.setChecked(false);
                rb2.setChecked(false);
                break;
        }
    }*/


    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        mMapView.onDestroy();
        baiduMap.setMyLocationEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
    }

    /**
     * butterknife绑定点击监听事件
     *
     * @param view
     */
    @OnClick({R.id.pupmenu, R.id.all_call, R.id.sign_out, R.id.contacts_container, R.id.bmapView})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.pupmenu:
                if (popupWindow.isShowing()) {
                    popupWindow.dismiss();
                } else {
                    popupWindow.showAsDropDown(rootView, 0, 0);
//                    popupWindow.showAtLocation(rootView,Gravity.TOP,0,24);
//                    rootView.getBackground().setAlpha(255);
//                    setBackgroundAlpha(0.5f);

                    popupWindow.update();
                }
                break;
            case R.id.all_call:
                break;
            case R.id.sign_out:
                break;
            case R.id.contacts_container:
                break;
            case R.id.bmapView:
                break;
        }
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.log_record:
                popupWindow.dismiss();
                break;
            case R.id.play_back:
                popupWindow.dismiss();
                break;
        }
    }

    @Override
    public void UpDateOrganization(JSONObject data) {
        Toast.makeText(this, "执行了", Toast.LENGTH_LONG).show();
        Log.e("Organization", data.toString());
    }

    private void initLocation() {
        //获取定位服务
        locationManager = (LocationManager) getSystemService(App.getInstance().getApplicationContext().LOCATION_SERVICE);
        //获取当前可用的位置控制器
        List<String> list = locationManager.getProviders(true);

        if (list.contains(LocationManager.GPS_PROVIDER)) {
            //是否为GPS位置控制器
            provider = LocationManager.GPS_PROVIDER;
        } else if (list.contains(LocationManager.NETWORK_PROVIDER)) {
            //是否为网络位置控制器
            provider = LocationManager.NETWORK_PROVIDER;

        } else {
            Toast.makeText(this, "请检查网络或GPS是否打开",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = locationManager.getLastKnownLocation(provider);
        if (location != null) {
            //获取当前位置，这里只用到了经纬度
            String string = "纬度为：" + location.getLatitude() + ",经度为："
                    + location.getLongitude();
        }

//绑定定位事件，监听位置是否改变
//第一个参数为控制器类型第二个参数为监听位置变化的时间间隔（单位：毫秒）
//第三个参数为位置变化的间隔（单位：米）第四个参数为位置监听器
        locationManager.requestLocationUpdates(provider, 2000, 2,
                locationListener);


        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        //可选，默认高精度，设置定位模式，高精度，低功耗，仅设备

        option.setCoorType("bd09ll");
        //可选，默认gcj02，设置返回的定位结果坐标系

        int span = 1000;
        option.setScanSpan(span);
        //可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的

        option.setIsNeedAddress(true);
        //可选，设置是否需要地址信息，默认不需要

        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy
        );//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        option.setCoorType("gcj02");//可选，默认gcj02，设置返回的定位结果坐标系
        //int span = 1000;
        //option.setScanSpan(span);//可选，默认0，即仅定位一次，设置发起定位请求的间隔需要大于等于1000ms才是有效的

        option.setOpenGps(true);
        //可选，默认false,设置是否使用gps

        option.setLocationNotify(true);
        //可选，默认false，设置是否当GPS有效时按照1S/1次频率输出GPS结果

        option.setIsNeedLocationDescribe(true);
        //可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”

        option.setIsNeedLocationPoiList(true);
        //可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到

        option.setIgnoreKillProcess(false);
        //可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死

        option.SetIgnoreCacheException(false);
        //可选，默认false，设置是否收集CRASH信息，默认收集

        option.setEnableSimulateGps(false);
        //可选，默认false，设置是否需要过滤GPS仿真结果，默认需要

        mLocationClient.setLocOption(option);
    }

    LocationListener locationListener = new LocationListener() {

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProviderEnabled(String arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProviderDisabled(String arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onLocationChanged(Location arg0) {
            // TODO Auto-generated method stub
            // 更新当前经纬度
            navigateTo(arg0);
        }
    };

    private void initMapViewLocation() {
        mMapView = (MapView) findViewById(R.id.bmapView);
        // 获取baiduMap对象
        baiduMap = mMapView.getMap();
        // 设置可改变地图位置
        baiduMap.setMyLocationEnabled(true);
        locationManager = (LocationManager) getSystemService(App.getInstance().getApplicationContext().LOCATION_SERVICE);
        List<String> list = locationManager.getProviders(true);
        if (list.contains(LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER;
        } else if (list.contains(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;

        } else {
            Toast.makeText(this, "当前不能提供位置信息", Toast.LENGTH_LONG).show();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = locationManager.getLastKnownLocation(provider);
        if (location != null) {
            navigateTo(location);
        }
        locationManager.requestLocationUpdates(provider, 5000, 1,
                locationListener);

    }

    private void navigateTo(Location location) {
        // 按照经纬度确定地图位置
        if (ifFrist) {
            LatLng ll = new LatLng(location.getLatitude(),
                    location.getLongitude());
            MapStatusUpdate update = MapStatusUpdateFactory.newLatLng(ll);
            // 移动到某经纬度
            baiduMap.animateMapStatus(update);
            update = MapStatusUpdateFactory.zoomBy(5f);
            // 放大
            baiduMap.animateMapStatus(update);

            ifFrist = false;
        }
        // 显示个人位置图标
        MyLocationData.Builder builder = new MyLocationData.Builder();
        builder.latitude(location.getLatitude());
        builder.longitude(location.getLongitude());
        MyLocationData data = builder.build();
        baiduMap.setMyLocationData(data);

    }
}
