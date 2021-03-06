package com.zrt.ptt.app.console.mvp.view.fragment;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapPoi;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.Overlay;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.map.TextureMapView;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.inner.Point;
import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.api.dto.LastLocationByUser;
import com.xianzhitech.ptt.api.dto.UserLocation;
import com.xianzhitech.ptt.broker.RoomMode;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.baidu.MyOrientationListener;
import com.zrt.ptt.app.console.mvp.bean.TraceListItemData;
import com.zrt.ptt.app.console.mvp.model.Node;
import com.zrt.ptt.app.console.mvp.presenter.ConsoleMapPresener;
import com.zrt.ptt.app.console.mvp.presenter.MainActivityPresenter;
import com.zrt.ptt.app.console.mvp.view.IView.IConsoMapView;
import com.zrt.ptt.app.console.mvp.view.IView.IMainActivityView;
import com.zrt.ptt.app.console.mvp.view.adapter.TraceGridAdapter;
import com.zrt.ptt.app.console.mvp.view.adapter.TraceHistoryListAdapter;
import com.zrt.ptt.app.console.mvp.view.dialog.DateDialog;

import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.reactivestreams.Subscription;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import utils.CommonUtil;
import utils.LogUtils;

import static java.sql.Types.NULL;


public class ConsoleMapFragment extends Fragment implements IConsoMapView,
        View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemClickListener,BaiduMap.OnMarkerClickListener,BaiduMap.OnPolylineClickListener{

    @BindView(R.id.textureBmapView)
    TextureMapView textureBmapView;
    @BindView(R.id.organiz_function_inc)
    LinearLayout organizFunctionInc;
    @BindView(R.id.sys_state_func_inc)
    LinearLayout sysStateFuncInc;
    @BindView(R.id.organiz_func_container)
    FrameLayout organizFuncContainer;
    Unbinder unbinder;
    private FrameLayout traceControlLayout;
    public LocationClient mLocationClient = null;
    private CheckBox traceRadioSelectedall;
    private GridView gridView;//轨迹回放选中用户Node
    private ListView listView;
    private TextView trace_start_time, trace_end_time, current_user, selected_user,trace_selected_user;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Disposable traceDisposable;
    @BindView(R.id.map_op_rect_select_surfaceview)
    MapRectSelectView mapRectSelectView;
    private MainActivityPresenter mainActivityPresenter;

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
    public BDLocationListener myListener;
    //定位都要通过LocationManager这个类实现
    private LocationManager locationManager;
    private String provider;
    BaiduMap baiduMap;
    boolean ifFrist = true;
    private List<Node> traceData;
    private TraceGridAdapter gridAdapter;
    private TraceHistoryListAdapter listAdapter;
    private List<TraceListItemData> userLocations = new ArrayList<>();
    private ConsoleMapPresener consoleMapPresener;
    private Subscription subscription;
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //轨迹回放
    private ArrayList<LatLng> routeList;// 路线点的集合
    /*
     * 自己写的json数据
     */
    private ProgressBar pb;// 进度条
    private ImageView trace_view_close, trace_play, trace_the_previous, trace_previous_loaction,
            trace_pause, trace_next_location, trace_next_user;
    private List<String> traceHistoryUserIds = new ArrayList<String>();//只负责装数据
    private DateDialog dateDialog;
    private String labels;
    private static final String PALY = "PALY";//播放
    private static final String PAUSE = "PAUSE";//暂停
    private static final String PREVIOUSSTEP = "PREVIOUSSTEP";//上一步
    private static final String NEXTSTEP = "NEXTSTEP";//下一步
    private static final String NEXT = "NEXT";//下一位
    private static final String LAST = "LAST";//上一位
    private Button btn_MapOP_RectSelLocation, btn_MapOP_RectSelPTT, btn_MapOP_RectSelConversion,
                    btn_MapOP_RectSelAudio,btn_MapOP_RectSelVidio,btn_MapOP_RectselMark;
    private DateDialog.Callback startTimeCallback = null;
    private DateDialog.Callback endTimeCallback = null;
    private long startTime = CommonUtil.getCurrentTime()*1000-12*60*60*1000;
    private long endTime = CommonUtil.getCurrentTime()*1000;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public ConsoleMapFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_console_map, container, false);
        unbinder = ButterKnife.bind(this, view);
        initView(view);
        initMap();
        return view;
    }

    private void initMap() {
        baiduMap = textureBmapView.getMap();
        textureBmapView.getChildAt(2).setPadding(0,0,0,300);
        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(15.0f);
        baiduMap.setMapStatus(msu);
        initMyLocation();
//        parseJson();
    }

    private void initView(View view) {
        View btnView = view.findViewById(R.id.organiz_function_inc);
        consoleMapPresener = new ConsoleMapPresener(this);
        traceControlLayout = (FrameLayout) view.findViewById(R.id.trace_control_layout);
        gridView = (GridView) traceControlLayout.findViewById(R.id.trace_user_label_grid);
        gridAdapter = new TraceGridAdapter(traceData, getActivity());
        gridView.setAdapter(gridAdapter);
        gridView.setOnItemClickListener(this);
        listView = (ListView) traceControlLayout.findViewById(R.id.tracehistory_listv);
        listAdapter = new TraceHistoryListAdapter(userLocations, getActivity());
        listView.setAdapter(listAdapter);
        trace_view_close = (ImageView) view.findViewById(R.id.trace_view_close);
        trace_start_time = (TextView) view.findViewById(R.id.trace_start_time);
        trace_end_time = (TextView) view.findViewById(R.id.trace_end_time);
        trace_selected_user = (TextView) view.findViewById(R.id.trace_selected_user);
        trace_start_time.setText(simpleDateFormat.format(startTime));
        trace_start_time.setOnClickListener(this);
        trace_end_time.setOnClickListener(this);
        trace_end_time.setText(simpleDateFormat.format(endTime));
        trace_view_close.setOnClickListener(this);
        btn_MapOP_RectSelLocation = (Button) btnView.findViewById(R.id.rectsel_location);
        btn_MapOP_RectSelConversion = (Button) btnView.findViewById(R.id.rectsel_conversion);
        btn_MapOP_RectSelPTT = (Button) btnView.findViewById(R.id.rectsel_ptt);
        btn_MapOP_RectSelAudio = (Button) btnView.findViewById(R.id.rectsel_audio);
        btn_MapOP_RectSelVidio = (Button) btnView.findViewById(R.id.rectsel_video);
        btn_MapOP_RectselMark = (Button) btnView.findViewById(R.id.rectsel_mark);
        trace_play = (ImageView) traceControlLayout.findViewById(R.id.trace_play);
        trace_the_previous = (ImageView) traceControlLayout.findViewById(R.id.trace_the_previous);
        trace_previous_loaction = (ImageView) traceControlLayout.findViewById(R.id.trace_previous_loaction);
        trace_pause = (ImageView) traceControlLayout.findViewById(R.id.trace_pause);
        trace_next_location = (ImageView) traceControlLayout.findViewById(R.id.trace_next_location);
        trace_next_user = (ImageView) traceControlLayout.findViewById(R.id.trace_next_user);
        btn_MapOP_RectSelLocation.setOnClickListener(this);
        btn_MapOP_RectSelConversion.setOnClickListener(this);
        btn_MapOP_RectSelPTT.setOnClickListener(this);
        btn_MapOP_RectSelAudio.setOnClickListener(this);
        btn_MapOP_RectSelVidio.setOnClickListener(this);
        btn_MapOP_RectselMark.setOnClickListener(this);
        trace_play.setOnClickListener(this);
        trace_the_previous.setOnClickListener(this);
        trace_previous_loaction.setOnClickListener(this);
        trace_pause.setOnClickListener(this);
        trace_next_location.setOnClickListener(this);
        trace_next_user.setOnClickListener(this);
        traceRadioSelectedall = (CheckBox) traceControlLayout.findViewById(R.id.trace_radio_selectedall);
        traceRadioSelectedall.setOnCheckedChangeListener(this);
        current_user = (TextView) view.findViewById(R.id.current_user);
        selected_user = (TextView) view.findViewById(R.id.selected_user);

    }

    /**
     * 解析json
     */
    /*private void parseJson() {
        try {
            JSONObject jObject = new JSONObject(routeJson);
            JSONArray jArray = jObject.getJSONArray("list");
            System.out.println(jArray.length());
            JSONObject object = null;
            routeList = new ArrayList<LatLng>();
            for (int i = 0; i < jArray.length(); i++) {
                object = jArray.getJSONObject(i);
                routeList.add(new LatLng(object.getDouble("lat"), object
                        .getDouble("lon")));
            }
            System.out.println("latlng size=" + routeList.size());
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }*/

    private void painStroke(ArrayList<LatLng> routeList) {
        baiduMap.clear();
        LatLng latLng = null;
        Marker marker = null;
        BitmapDescriptor mMarker = BitmapDescriptorFactory.fromResource(R.drawable.location_point);//icon_geo
        OverlayOptions options;

        // 经纬度
        latLng = routeList.get(routeList.size() / 2);
        // 图标

        //屏蔽代码属于描述maker展示的详情
            /*Bundle arg0 = new Bundle();
            arg0.putSerializable("info", latLin);
            marker.setExtraInfo(arg0);*/

//        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(15.0f);
        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(latLng);
        baiduMap.setMapStatus(msu);
        msu = MapStatusUpdateFactory.zoomTo(15.0f);
        baiduMap.setMapStatus(msu);
        //构造纹理资源
        BitmapDescriptor custom1 = BitmapDescriptorFactory
                .fromResource(R.drawable.icon_road_red_arrow);
        BitmapDescriptor custom2 = BitmapDescriptorFactory
                .fromResource(R.drawable.icon_road_green_arrow);
        BitmapDescriptor custom3 = BitmapDescriptorFactory
                .fromResource(R.drawable.icon_road_blue_arrow);
// 定义点
       /* LatLng pt1 = new LatLng(39.93923, 116.357428);
        LatLng pt2 = new LatLng(39.91923, 116.327428);
        LatLng pt3 = new LatLng(39.89923, 116.347428);
        LatLng pt4 = new LatLng(39.89923, 116.367428);
        LatLng pt5 = new LatLng(39.91923, 116.387428);*/

//构造纹理队列
        List<BitmapDescriptor> customList = new ArrayList<BitmapDescriptor>();
        customList.add(custom1);
        customList.add(custom2);
        customList.add(custom3);

//        List<LatLng> points = new ArrayList<LatLng>();
        List<LatLng> points = routeList;
        List<Integer> index = new ArrayList<Integer>();
//        points.add(pt1);//点元素
        index.add(0);//设置该点的纹理索引
//        points.add(pt2);//点元素
        index.add(0);//设置该点的纹理索引
//        points.add(pt3);//点元素
        index.add(1);//设置该点的纹理索引
//        points.add(pt4);//点元素
        index.add(2);//设置该点的纹理索引
//        points.add(pt5);//点元素
        for (LatLng lat : points) {
            options = new MarkerOptions().position(lat).icon(mMarker)
                    .zIndex(5);
            baiduMap.addOverlay(options);
        }
//构造对象
        OverlayOptions ooPolyline = new PolylineOptions().width(10).color(R.color.layout_title).points(points);//.customTextureList(customList).textureIndex(index);
//添加到地图
        baiduMap.addOverlay(ooPolyline);
    }

    @Override
    public void onClick(View v) {
//        painStroke(routeList);
      /*  MapMoveUtil map = new MapMoveUtil(bmapView,baiduMap);
        map.moveLooper();*/
        switch (v.getId()) {
            case R.id.rectsel_location:
            {
                mapRectSelect(null);
            }
                break;
            case R.id.rectsel_conversion:
            {
                mapRectSelect(RoomMode.Conversion);
            }
                break;
            case R.id.rectsel_ptt:
            {
                mapRectSelect(RoomMode.NORMAL);
            }
                break;
            case R.id.rectsel_audio:
            {
                mapRectSelect(RoomMode.AUDIO);
            }
                break;
            case R.id.rectsel_video:
            {
                mapRectSelect(RoomMode.VIDEO);
            }
                break;
            case R.id.rectsel_mark:
                baiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(LatLng latLng) {

                    }

                    @Override
                    public boolean onMapPoiClick(MapPoi mapPoi) {
                        return false;
                    }
                });
                break;
            case R.id.trace_view_close:
                traceControlLayout.setVisibility(View.INVISIBLE);
                break;
            case R.id.trace_play://播放
                labels = PALY;
                if (observable == null) {
                    observable = Observable.interval(1, 1, TimeUnit.SECONDS);
                }
                setDisposeClick();
                if (userLocations.size() > 0 &&
                        userLocations.get(0).getName().equals(traceHistoryUserIds.get(0))) {
                    showTrackPlayback(userLocations);
                } else {
                    consoleMapPresener.showUserTraceHistory(traceHistoryUserIds, this, startTime, endTime);
                }

                for (Node node : adpterNodes) {
                    if (node.get_id().equals(traceHistoryUserIds.get(0))) {
                        current_user.setText(node.getName());
                    }
                }
                break;
            case R.id.trace_pause://暂停
                setDisposeClick();
                labels = PAUSE;
                break;
            case R.id.trace_previous_loaction://上一位置
                setDisposeClick();
                labels = PREVIOUSSTEP;
                if(num>0){
                    --num;
                }
                if(num>=0){
                    loactionMove(moveMarker);
                }
//                trace_next_location.setClickable(true);
                if(num==0){
                    Toast.makeText(getActivity(),"已是第一条定位数据，前面已没有数据了",Toast.LENGTH_LONG).show();
                    return;
//                    trace_previous_loaction.setClickable(false);
                }
                break;
            case R.id.trace_next_location://下一位置
                setDisposeClick();
                labels = NEXTSTEP;
                if(num<userLocations.size()-1){
                    num++;
                }
                if(num<=userLocations.size()-1){
                    loactionMove(moveMarker);
                }
//                trace_previous_loaction.setClickable(true);
                if(num==userLocations.size()-1){
                    Toast.makeText(getActivity(),"已是最后一条定位数据，没有更多数据了",Toast.LENGTH_LONG).show();
                    return;
//                    trace_next_location.setClickable(false);
                }
                break;
            case R.id.trace_the_previous://上一个用户
                if (observable == null) {
                    observable = Observable.interval(1, 1, TimeUnit.SECONDS);
                }
                setDisposeClick();
                labels = LAST;
                int position = 0;
                for (int i = 0; i < adpterNodes.size(); i++) {
                    if (adpterNodes.get(i).get_id().equals(traceHistoryUserIds.get(0))) {
                        position = i;
                        break;
                    }
                }
                for (int k = 0; k < adpterNodes.size(); k++) {
                    if(position==0){
                        Toast.makeText(getActivity(),"前面已没有用户了，请选择下一位",Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (k == position - 1) {
                        adpterNodes.get(k).setSelected(true);
                        traceHistoryUserIds.clear();
                        traceHistoryUserIds.add(adpterNodes.get(k).get_id());
                        selected_user.setText(adpterNodes.get(k).getName());
                        current_user.setText(adpterNodes.get(k).getName());
                    } else {
                        adpterNodes.get(k).setSelected(false);
                    }
                }
            gridAdapter.setTraceData(adpterNodes);
            consoleMapPresener.showUserTraceHistory(traceHistoryUserIds, this, startTime, endTime);
            break;
            case R.id.trace_next_user://下一用户
                trace_play.setClickable(false);
                if (observable == null) {
                    observable = Observable.interval(1, 1, TimeUnit.SECONDS);
                }
                setDisposeClick();
                labels = NEXT;
                int posNext = 0;
                for (int i = 0; i < adpterNodes.size(); i++) {
                    if (adpterNodes.get(i).get_id().equals(traceHistoryUserIds.get(0))) {
                        posNext = i;
                        break;
                    }
                }
                for (int k = 0; k < adpterNodes.size(); k++) {
                    if(posNext==adpterNodes.size()-1){
                        Toast.makeText(getActivity(),"已经是最后一位用户，请选择上一位",Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (k == posNext + 1) {
                        adpterNodes.get(k).setSelected(true);
                        traceHistoryUserIds.clear();
                        traceHistoryUserIds.add(adpterNodes.get(k).get_id());
                        selected_user.setText(adpterNodes.get(k).getName());
                        current_user.setText(adpterNodes.get(k).getName());
                    } else {
                        adpterNodes.get(k).setSelected(false);
                    }
                }
                gridAdapter.setTraceData(adpterNodes);
                consoleMapPresener.showUserTraceHistory(traceHistoryUserIds, this, startTime, endTime);
                break;
            case R.id.trace_start_time:
                if (null == startTimeCallback) {
                    startTimeCallback = new DateDialog.Callback() {
                        @Override
                        public void onDateCallback(long timeStamp) {
                            startTime = timeStamp * 1000;
                            StringBuilder startTimeBuilder = new StringBuilder();
//                            startTimeBuilder.append(getResources().getString(R.string.start_time));
                            startTimeBuilder.append(simpleDateFormat.format(timeStamp * 1000));
                            trace_start_time.setText(startTimeBuilder.toString());
                        }
                    };
                }
                if (null == dateDialog) {
                    dateDialog = new DateDialog(getActivity(), startTimeCallback);
                } else {
                    dateDialog.setCallback(startTimeCallback);
                }
                dateDialog.show();
                break;
            case R.id.trace_end_time:
                if (null == endTimeCallback) {
                    endTimeCallback = new DateDialog.Callback() {
                        @Override
                        public void onDateCallback(long timeStamp) {
                            endTime = timeStamp * 1000;
                            StringBuilder endTimeBuilder = new StringBuilder();
//                            endTimeBuilder.append(getResources().getString(R.string.end_time));
                            endTimeBuilder.append(simpleDateFormat.format(timeStamp * 1000));
                            trace_end_time.setText(endTimeBuilder.toString());
                        }
                    };
                }
                if (null == dateDialog) {
                    dateDialog = new DateDialog(getActivity(), endTimeCallback);
                } else {
                    dateDialog.setCallback(endTimeCallback);
                }
                dateDialog.show();
                break;
        }
    }

    private void setDisposeClick() {
        trace_play.setClickable(true);
        if (timerdisposable != null && !timerdisposable.isDisposed())
            timerdisposable.dispose();
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
        try {
            mainActivityPresenter = new MainActivityPresenter((IMainActivityView) context);
        }catch (ClassCastException e) {
            throw new ClassCastException(getActivity().getClass().getName()
                    +" must implements interface MyListener");
        }
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

    //maker点击事件
    @Override
    public boolean onMarkerClick(Marker marker) {
        LatLng latLng = marker.getPosition();
        for(TraceListItemData data :userLocations){
            if(data.getLatLng().equals(latLng)){
                setDisposeClick();
                num = userLocations.indexOf(data);
                loactionMove(moveMarker);
            }
        }
        return false;
    }

    //轨迹先点击事件
    @Override
    public boolean onPolylineClick(Polyline polyline) {
//        Toast.makeText(getActivity(),"polyline:"+polyline,Toast.LENGTH_LONG).show();
        return false;
    }


    private class myMapTouchListener implements BaiduMap.OnMapTouchListener {

        @Override
        public void onTouch(MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    System.out.print("down");
                    break;
                case MotionEvent.ACTION_UP:
                    System.out.print("up");
                    break;
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // 开启图层定位
        baiduMap.setMyLocationEnabled(true);
        baiduMap.setOnMarkerClickListener(this);
        baiduMap.setOnPolylineClickListener(this);
        baiduMap.setOnMapTouchListener(new myMapTouchListener());
        if (!mLocationClient.isStarted()) {
            mLocationClient.start();
        }
        // 开启方向传感器
//        myOrientationListener.start();

    }

    @Override
    public void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        textureBmapView.onResume();
//                addRouteLine(routeList);// 添加路线

    }

    @Override
    public void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        textureBmapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        // 关闭图层定位
        baiduMap.setMyLocationEnabled(false);
        mLocationClient.stop();

        // 关闭方向传感器
//        myOrientationListener.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        if (textureBmapView != null) {
            textureBmapView.onDestroy();
        }
        compositeDisposable.clear();
        baiduMap.setMyLocationEnabled(false);
    }

    //根据原始数据计算中心坐标和缩放级别，并为地图设置中心坐标和缩放级别。
    private void setZoom(List<LastLocationByUser> lastLocationByUsers) {
        if (lastLocationByUsers.size() > 0) {
            double maxLng = lastLocationByUsers.get(0).getLatLng().getLng();
            double minLng = lastLocationByUsers.get(0).getLatLng().getLng();
            double maxLat = lastLocationByUsers.get(0).getLatLng().getLat();
            double minLat = lastLocationByUsers.get(0).getLatLng().getLat();
            com.xianzhitech.ptt.data.LatLng res;
            for (LastLocationByUser usr : lastLocationByUsers) {
                res = usr.getLatLng();
                if (res.getLng() > maxLng) maxLng = res.getLng();
                if (res.getLng() < minLng) minLng = res.getLng();
                if (res.getLat() > maxLat) maxLat = res.getLat();
                if (res.getLat() < minLat) minLat = res.getLat();
            }
            ;
            double cenLng = (maxLng + minLng) / 2;
            double cenLat = (maxLat + minLat) / 2;
            float zoom = getZoom(maxLng, minLng, maxLat, minLat);

            MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(
                    new LatLng(cenLat, cenLng));
            baiduMap.setMapStatus(msu);
            msu = MapStatusUpdateFactory.zoomTo(zoom);
            baiduMap.setMapStatus(msu);
            //map.centerAndZoom(new BMap.Point(cenLng,cenLat), zoom);
        } else {
            //没有坐标，显示全中国
            //map.centerAndZoom(new BMap.Point(103.388611,35.563611), 5);
        }
    }

    //根据轨迹原始数据计算中心坐标和缩放级别，并为地图设置中心坐标和缩放级别。
    private void setTraceZoom(List<LatLng> latLngs) {
        LatLng resd;
        if (latLngs.size() > 0) {
            double maxLng = latLngs.get(0).longitude;
            double minLng = latLngs.get(0).longitude;
            ;
            double maxLat = latLngs.get(0).latitude;
            double minLat = latLngs.get(0).latitude;
            com.xianzhitech.ptt.data.LatLng res;
            for (LatLng usr : latLngs) {
                resd = usr;
                if (resd.longitude > maxLng) maxLng = resd.longitude;
                if (resd.longitude < minLng) minLng = resd.longitude;
                if (resd.latitude > maxLat) maxLat = resd.latitude;
                if (resd.latitude < minLat) minLat = resd.latitude;
            }
            ;
            double cenLng = (maxLng + minLng) / 2;
            double cenLat = (maxLat + minLat) / 2;
            float zoom = getZoom(maxLng, minLng, maxLat, minLat);

            MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(
                    new LatLng(cenLat, cenLng));
            baiduMap.setMapStatus(msu);
            msu = MapStatusUpdateFactory.zoomTo(zoom);
            baiduMap.setMapStatus(msu);
            //map.centerAndZoom(new BMap.Point(cenLng,cenLat), zoom);
        } else {
            //没有坐标，显示全中国
            //map.centerAndZoom(new BMap.Point(103.388611,35.563611), 5);
        }
    }

    public static double getDistance(double lat_a, double lng_a, double lat_b, double lng_b) {
        double pk = 180 / 3.14169;
        double a1 = lat_a / pk;
        double a2 = lng_a / pk;
        double b1 = lat_b / pk;
        double b2 = lng_b / pk;
        double t1 = Math.cos(a1) * Math.cos(a2) * Math.cos(b1) * Math.cos(b2);
        double t2 = Math.cos(a1) * Math.sin(a2) * Math.cos(b1) * Math.sin(b2);
        double t3 = Math.sin(a1) * Math.sin(b1);
        double tt = Math.acos(t1 + t2 + t3);
        return 6371000 * tt;
    }

    //根据经纬极值计算绽放级别。
    private float getZoom(double maxLng, double minLng, double maxLat, double minLat) {
        int[] zoom = {50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 25000, 50000, 100000, 200000, 500000, 1000000, 2000000};//级别18到3。
//        var pointA = new BMap.Point(maxLng,maxLat);  // 创建点坐标A
//        var pointB = new BMap.Point(minLng,minLat);  // 创建点坐标B
        //var distance = map.getDistance(pointA,pointB).toFixed(1);  //获取两点距离,保留小数点后两位
        double distance = getDistance(maxLat, maxLng, minLat, minLng);
        for (int i = 0, zoomLen = zoom.length; i < zoomLen; i++) {
            if (zoom[i] - distance > 0) {
                return 18 - i + 3;//之所以会多3，是因为地图范围常常是比例尺距离的10倍以上。所以级别会增加3。
            }
        }
        return 0;
    }

    @Override
    public void showLocations(List<LastLocationByUser> lastLocationByUsers) {
        baiduMap.clear();
        List<OverlayOptions> optionList = new ArrayList<OverlayOptions>();
        BitmapDescriptor mMarker = BitmapDescriptorFactory.fromResource(R.drawable.icon_marka);
        for (LastLocationByUser user : lastLocationByUsers) {
            LatLng gpsLng = new LatLng(user.getLatLng().getLat(), user.getLatLng().getLng());
            OverlayOptions options = new MarkerOptions().position(gpsLng).icon(mMarker);
            optionList.add(options);
        }

        baiduMap.addOverlays(optionList);

        setZoom(lastLocationByUsers);

    }

    private void findNearbyPeople(Point startPoint, Point endPoint, RoomMode roomMode) {
        if (startPoint.x == endPoint.x && startPoint.y == endPoint.y) {
            return;
        }
        LatLng topLeft = baiduMap.getProjection().fromScreenLocation(
                new android.graphics.Point(startPoint.x, startPoint.y));
        LatLng bottomRight = baiduMap.getProjection().fromScreenLocation(
                new android.graphics.Point(endPoint.x, endPoint.y));



        ((AppComponent) App.getInstance().getApplicationContext())
                .getSignalBroker()
                .findNearbyPeople(new com.xianzhitech.ptt.data.LatLng(topLeft.latitude, topLeft.longitude),
                        new com.xianzhitech.ptt.data.LatLng(bottomRight.latitude, bottomRight.longitude))
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable1 -> {
                    //应该被保存起来，统一释放，以免泄露
                })
                .subscribe(
                        rsult -> {
                            mapRectSelectView.setVisibility(View.INVISIBLE);

                            if (rsult.size() == 0) {
                                Toast.makeText(textureBmapView.getContext(),
                                        R.string.tip_rect_select_no_people, Toast.LENGTH_LONG).show();
                                return;
                            }

                            handleRectSel(rsult,roomMode);
                        },
                        err -> {
                            mapRectSelectView.setVisibility(View.INVISIBLE);
                            Toast.makeText(textureBmapView.getContext(), err.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                );
    }

    private void handleRectSel(List<UserLocation> rsult, RoomMode roomMode)
    {
        List<LastLocationByUser> oneLineUsersLocList = new ArrayList<LastLocationByUser>();
        List<String> usermultiMediaIds = new ArrayList<>();
        for (UserLocation ul : rsult) {
            LastLocationByUser llb = new LastLocationByUser(ul.getUserId(),
                    ul.getLocation().getLatLng(),
                    "");
            oneLineUsersLocList.add(llb);
            usermultiMediaIds.add(ul.getUserId());
        }

        showLocations(oneLineUsersLocList);

        if (roomMode == null)
        {
            return;
        }

//        if (roomMode != RoomMode.Conversion)
//        {
//            mainActivityPresenter.showChatkRoom(usermultiMediaIds, new ArrayList<>(), RoomMode.Conversion);
//        }
        mainActivityPresenter.showChatkRoom(usermultiMediaIds, new ArrayList<>(), roomMode);
//
//        switch (roomMode)
//        {
//            case Conversion:
//            {
//                mainActivityPresenter.showChatkRoom(usermultiMediaIds, new ArrayList<>(), roomMode);
//            }
//                break;
//            case NORMAL://对讲
//            {
//                mainActivityPresenter.showChatkRoom(usermultiMediaIds, new ArrayList<>(), roomMode);
//            }
//                break;
//            case VIDEO:
//                break;
//            case AUDIO:
//                break;
//            case BROADCAST:
//                break;
//            case SYSTEM_CALL:
//                break;
//            case EMERGENCY:
//                break;
//            default:
//
//                break;
//        }
    }


    @Override
    public void mapRectSelect(RoomMode roomMode) {
        //rectSelect
        mapRectSelectView.setAlpha(0.3f);
        mapRectSelectView.addRectSelectedCallBack(new MapRectSelectView.RectSelectedCallBack() {
            @Override
            public void rectSelected(Point startPoint, Point endPoint) {

                findNearbyPeople(startPoint, endPoint, roomMode);
            }
        });
        mapRectSelectView.setVisibility(View.VISIBLE);
    }

    @Override
    public void showHistoryDialog() {
//        new TrackPlayBackDialog(getActivity()).show();
        traceControlLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public int getLayoutVisibility() {
        return traceControlLayout.getVisibility();
    }

    private List<Node> adpterNodes = new ArrayList<>();

    private void addNodes() {

    }

    @Override
    public void sendCheckedUsers(List<Node> checkedNodes) {
        adpterNodes.clear();
        for (Node node : checkedNodes) {
            try {
                adpterNodes.add(node.cloneNode());
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }

        boolean exsitSelected = false;
        traceHistoryUserIds.clear();
        for (Node node : adpterNodes) {
            if (selectedNodes.size() > 0 && node.equals(selectedNodes.get(0))) {
                node.setSelected(true);
                exsitSelected = true;
                selected_user.setText(node.getName());
                trace_selected_user.setText("1");
                traceHistoryUserIds.add(node.get_id());
                break;
            }
        }
        if (adpterNodes.size() > 0 && !exsitSelected) {
            adpterNodes.get(0).setSelected(true);
            selected_user.setText(adpterNodes.get(0).getName());
            try {
                selectedNodes.clear();
                selectedNodes.add(adpterNodes.get(0).cloneNode());
                trace_selected_user.setText("1");
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
            traceHistoryUserIds.add(adpterNodes.get(0).get_id());

        }

        gridAdapter.setTraceData(adpterNodes);
    }

    private int num = 0;//轨迹列表数据滚动计数器
    private Observable<Long> observable;
    private Disposable timerdisposable;
    private ArrayList<LatLng> ltns = new ArrayList<>();
    private Overlay myOverlay;
    private BitmapDescriptor moveMarker = BitmapDescriptorFactory.fromResource(R.drawable.navigation);

    //拿到历史轨迹数据，业务在这里处理
    @Override
    public void showTrackPlayback(List<TraceListItemData> datas) {
        if(datas.size()==0){
            Toast.makeText(getActivity(),"该时间段内没有用户定位数据，请换个时间段",Toast.LENGTH_SHORT).show();
            baiduMap.clear();
            userLocations.clear();
            listAdapter.setUserLocations(userLocations);
            return;
        }
        if (datas.size() > 0 && userLocations.size() > 0 &&
                userLocations.get(0).getName().equals(datas.get(0).getName())) {
            listAdapter.setUserLocations(userLocations);
            for (int i = 0; i < userLocations.size(); i++) {
                if (i == num) {
                    userLocations.get(i).setCurrent(true);
                } else {
                    userLocations.get(i).setCurrent(false);
                }
            }
            listView.setSelection(num);
        } else {

            userLocations.clear();
            num = 0;
            for (TraceListItemData listItem : datas) {
                try {
                    userLocations.add(listItem.cloneNode());
                    ltns.add(listItem.getLatLng());
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
            }
            listView.setSelection(0);
        }
        if (ltns.size() > 0) {
            painStroke(ltns);
            setTraceZoom(ltns);
        }
        listAdapter.setUserLocations(userLocations);
        observable.take(userLocations.size() + 1)
                .subscribe(new Observer<Long>() {
                    @Override
                    public void onSubscribe(Disposable disposable1) {
                        timerdisposable = disposable1;
                    }

                    @Override
                    public void onNext(Long aLong) {
                        switch (labels) {
                            case PALY:
                                playNextLast();
                                break;
                            case NEXT://上一位
                                playNextLast();
                                break;
                            case LAST://下一位
                                playNextLast();
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    //播放，上一位，下一位逻辑
    private void playNextLast(){
        if (num < userLocations.size()) {
            loactionMove(moveMarker);
            num++;
        } else {
            //当 aLong == listview 适配器数据的count 的时候调用 disabl.dispo()   cancel  this  subscriptpion
            if (num == userLocations.size()) {
                num = 0;
            }
        }
    }
    //轨迹播放listview动态滚动与轨迹跳动
    private void loactionMove(BitmapDescriptor mMarker) {
        listView.post(new Runnable() {
            @Override
            public void run() {
//              listView.smoothScrollToPosition(num);
                listView.smoothScrollToPositionFromTop(num, 0);
                for (int i = 0; i < userLocations.size(); i++) {
                    if (i == num) {
                        if (i > 0 && myOverlay != null) {
                            myOverlay.remove();
                        }
                        userLocations.get(i).setCurrent(true);
                        LatLng gpsLng = new LatLng(userLocations.get(i).getLatLng().latitude,
                                userLocations.get(i).getLatLng().longitude);
                        OverlayOptions overlayOptions = new MarkerOptions().position(gpsLng).icon(mMarker).zIndex(6);
                        myOverlay = baiduMap.addOverlay(overlayOptions);
                    } else {
                        userLocations.get(i).setCurrent(false);
                    }
                }
                listAdapter.setUserLocations(userLocations);
            }
        });
    }

    @Override
    public void callBackiDisposable(Disposable disposable) {
//        this.disposable = disposable;
        if (disposable != null) {
            compositeDisposable.add(disposable);
        }
    }

    public int getVisibiliyControlLayout() {
        return traceControlLayout.getVisibility();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//        Toast.makeText(getActivity(), "isChecked" + isChecked, Toast.LENGTH_SHORT).show();

    }


    private List<Node> selectedNodes = new ArrayList<>();

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Node node = (Node) parent.getAdapter().getItem(position);
        selectedNodes.clear();
        traceHistoryUserIds.clear();
        traceHistoryUserIds.add(node.get_id());
        for (Node bean : adpterNodes) {
            if (bean.get_id().equals(node.get_id())) {
                bean.setSelected(true);
                trace_selected_user.setText("1");
                try {
                    selectedNodes.add(bean.cloneNode());
                } catch (CloneNotSupportedException e) {
                    e.printStackTrace();
                }
            } else {
                bean.setSelected(false);
            }
        }
        ((TraceGridAdapter) parent.getAdapter()).setTraceData(adpterNodes);
        selected_user.setText(node.getName());

    }


    public class MyLocationListener implements BDLocationListener {

        @Override
        public void onReceiveLocation(BDLocation location) {

            // map view 销毁后不在处理新接收的位置
            if (location == null || textureBmapView == null)
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
                    .direction(mXDirection).latitude(location.getLatitude())
                    .longitude(location.getLongitude()).build();
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
            if (isFristLocation) {
                LatLng ll = new LatLng(location.getLatitude(),
                        location.getLongitude());
                MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
                baiduMap.animateMapStatus(u);
                isFristLocation = false;
            }
        }

        @Override
        public void onConnectHotSpotMessage(String s, int i) {

        }
    }

    /**
     * 初始化方向传感器
     */
    private void initOritationListener() {
        myOrientationListener = new MyOrientationListener(
                App.getInstance().getApplicationContext());
        myOrientationListener
                .setOnOrientationListener(new MyOrientationListener.OnOrientationListener() {
                    @Override
                    public void onOrientationChanged(float x) {
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
