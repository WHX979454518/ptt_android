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
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.map.TextureMapView;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.inner.Point;
import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.api.dto.LastLocationByUser;
import com.xianzhitech.ptt.api.dto.UserLocation;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.baidu.MyOrientationListener;
import com.zrt.ptt.app.console.mvp.bean.TraceListItemData;
import com.zrt.ptt.app.console.mvp.model.Node;
import com.zrt.ptt.app.console.mvp.presenter.ConsoleMapPresener;
import com.zrt.ptt.app.console.mvp.view.IView.IConsoMapView;
import com.zrt.ptt.app.console.mvp.view.adapter.TraceGridAdapter;
import com.zrt.ptt.app.console.mvp.view.adapter.TraceHistoryListAdapter;
import com.zrt.ptt.app.console.mvp.view.dialog.DateDialog;

import android.widget.TextView;
import android.widget.Toast;

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


public class ConsoleMapFragment extends Fragment implements IConsoMapView,
        View.OnClickListener, CompoundButton.OnCheckedChangeListener,
        AdapterView.OnItemClickListener {

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
    private TextView trace_start_time, trace_end_time, current_user, selected_user;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Disposable traceDisposable;
    @BindView(R.id.map_op_rect_select_surfaceview)
    MapRectSelectView mapRectSelectView;

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
    private String routeJson = "{\"list\":[{\"lon\":122.235502,\"lat\":37.330564},{\"lon\":122.235727,\"lat\":37.328527},{\"lon\":122.236086,\"lat\":37.326676},{\"lon\":122.23694,\"lat\":37.325083},{\"lon\":122.237218,\"lat\":37.324703},{\"lon\":122.238862,\"lat\":37.323153},{\"lon\":122.240982,\"lat\":37.321596},{\"lon\":122.243381,\"lat\":37.319867},{\"lon\":122.245267,\"lat\":37.318454},{\"lon\":122.247504,\"lat\":37.316804},{\"lon\":122.249696,\"lat\":37.315182},{\"lon\":122.251762,\"lat\":37.313661},{\"lon\":122.253451,\"lat\":37.312075},{\"lon\":122.254547,\"lat\":37.31008},{\"lon\":122.25478,\"lat\":37.307677},{\"lon\":122.254196,\"lat\":37.30523},{\"lon\":122.253478,\"lat\":37.302603},{\"lon\":122.252939,\"lat\":37.300565},{\"lon\":122.25231,\"lat\":37.298663},{\"lon\":122.251735,\"lat\":37.297156},{\"lon\":122.251223,\"lat\":37.296022},{\"lon\":122.250253,\"lat\":37.293962},{\"lon\":122.249282,\"lat\":37.291895},{\"lon\":122.24816,\"lat\":37.289519},{\"lon\":122.24718,\"lat\":37.287431},{\"lon\":122.246102,\"lat\":37.285155},{\"lon\":122.245105,\"lat\":37.283081},{\"lon\":122.243938,\"lat\":37.280604},{\"lon\":122.24294,\"lat\":37.278458},{\"lon\":122.241782,\"lat\":37.275823},{\"lon\":122.241135,\"lat\":37.271222},{\"lon\":122.241458,\"lat\":37.269096},{\"lon\":122.242303,\"lat\":37.266799},{\"lon\":122.243372,\"lat\":37.264587},{\"lon\":122.244557,\"lat\":37.262189},{\"lon\":122.245761,\"lat\":37.259741},{\"lon\":122.247001,\"lat\":37.257386},{\"lon\":122.248097,\"lat\":37.255167},{\"lon\":122.249327,\"lat\":37.252668},{\"lon\":122.25081,\"lat\":37.249709},{\"lon\":122.252121,\"lat\":37.24698},{\"lon\":122.253118,\"lat\":37.244502},{\"lon\":122.253549,\"lat\":37.243202},{\"lon\":122.253657,\"lat\":37.242929},{\"lon\":122.253936,\"lat\":37.241845},{\"lon\":122.254277,\"lat\":37.240272},{\"lon\":122.2546,\"lat\":37.238527},{\"lon\":122.255032,\"lat\":37.23625},{\"lon\":122.25566,\"lat\":37.23296},{\"lon\":122.257026,\"lat\":37.227767},{\"lon\":122.258221,\"lat\":37.225303},{\"lon\":122.258993,\"lat\":37.224017},{\"lon\":122.259029,\"lat\":37.223952},{\"lon\":122.259415,\"lat\":37.223471},{\"lon\":122.260161,\"lat\":37.222429},{\"lon\":122.261041,\"lat\":37.221193},{\"lon\":122.261931,\"lat\":37.219979},{\"lon\":122.263053,\"lat\":37.218449},{\"lon\":122.263871,\"lat\":37.217256},{\"lon\":122.264814,\"lat\":37.215597},{\"lon\":122.265479,\"lat\":37.214038},{\"lon\":122.265542,\"lat\":37.21398},{\"lon\":122.265641,\"lat\":37.21375},{\"lon\":122.265955,\"lat\":37.212744},{\"lon\":122.266224,\"lat\":37.211472},{\"lon\":122.266476,\"lat\":37.208879},{\"lon\":122.266575,\"lat\":37.206436},{\"lon\":122.266656,\"lat\":37.204244},{\"lon\":122.266745,\"lat\":37.201901},{\"lon\":122.266862,\"lat\":37.199501},{\"lon\":122.266961,\"lat\":37.197015},{\"lon\":122.267042,\"lat\":37.194622},{\"lon\":122.26715,\"lat\":37.192164},{\"lon\":122.267518,\"lat\":37.189109},{\"lon\":122.268488,\"lat\":37.186536},{\"lon\":122.270042,\"lat\":37.184099},{\"lon\":122.271722,\"lat\":37.181749},{\"lon\":122.273168,\"lat\":37.179729},{\"lon\":122.274588,\"lat\":37.177652},{\"lon\":122.275836,\"lat\":37.175682},{\"lon\":122.276303,\"lat\":37.174518},{\"lon\":122.276699,\"lat\":37.173756},{\"lon\":122.276735,\"lat\":37.17359},{\"lon\":122.276995,\"lat\":37.172756},{\"lon\":122.277283,\"lat\":37.171354},{\"lon\":122.277525,\"lat\":37.16978},{\"lon\":122.277849,\"lat\":37.16781},{\"lon\":122.278244,\"lat\":37.165251},{\"lon\":122.278585,\"lat\":37.163065},{\"lon\":122.27898,\"lat\":37.160772},{\"lon\":122.279187,\"lat\":37.159326},{\"lon\":122.279223,\"lat\":37.159017},{\"lon\":122.279313,\"lat\":37.158607},{\"lon\":122.279474,\"lat\":37.157637},{\"lon\":122.279753,\"lat\":37.156191},{\"lon\":122.280409,\"lat\":37.153826},{\"lon\":122.280777,\"lat\":37.152977},{\"lon\":122.2819,\"lat\":37.150849},{\"lon\":122.283113,\"lat\":37.14854},{\"lon\":122.284406,\"lat\":37.146139},{\"lon\":122.286023,\"lat\":37.143082},{\"lon\":122.287919,\"lat\":37.139608},{\"lon\":122.289365,\"lat\":37.136911},{\"lon\":122.290443,\"lat\":37.134574},{\"lon\":122.291036,\"lat\":37.132172},{\"lon\":122.29117,\"lat\":37.129582},{\"lon\":122.291206,\"lat\":37.127165},{\"lon\":122.291251,\"lat\":37.124784},{\"lon\":122.291305,\"lat\":37.122813},{\"lon\":122.29126,\"lat\":37.122144},{\"lon\":122.29126,\"lat\":37.122144},{\"lon\":122.291269,\"lat\":37.122159},{\"lon\":122.291278,\"lat\":37.122144},{\"lon\":122.291278,\"lat\":37.122166},{\"lon\":122.291296,\"lat\":37.12218}]}";
    private ProgressBar pb;// 进度条
    private ImageView playIv, trace_view_close, trace_play, trace_the_previous, trace_previous_loaction, trace_pause, trace_next_location, trace_next_user;
    private List<String> traceHistoryUserIds = new ArrayList<String>();//只负责装数据
    private DateDialog dateDialog;
    private String labels;
    private static final String PALY = "PALY";//播放
    private static final String PAUSE = "PAUSE";//暂停
    private static final String PREVIOUSSTEP = "PREVIOUSSTEP";//上一步
    private static final String NEXTSTEP = "NEXTSTEP";//下一步
    private static final String NEXT = "NEXT";//下一位
    private static final String LAST = "LAST";//上一位

    public ConsoleMapFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_console_map, container, false);
        unbinder = ButterKnife.bind(this, view);
//        initTrace(inflater);
        initView(view);
        initMap();
        return view;
    }

    private void initMap() {
        baiduMap = textureBmapView.getMap();
        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(15.0f);

        baiduMap.setMapStatus(msu);
        initMyLocation();
        parseJson();
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
        trace_start_time.setOnClickListener(this);
        trace_end_time.setOnClickListener(this);
        trace_view_close.setOnClickListener(this);
        playIv = (ImageView) btnView.findViewById(R.id.location_ivPlay);
        trace_play = (ImageView) traceControlLayout.findViewById(R.id.trace_play);
        trace_the_previous = (ImageView) traceControlLayout.findViewById(R.id.trace_the_previous);
        trace_previous_loaction = (ImageView) traceControlLayout.findViewById(R.id.trace_previous_loaction);
        trace_pause = (ImageView) traceControlLayout.findViewById(R.id.trace_pause);
        trace_next_location = (ImageView) traceControlLayout.findViewById(R.id.trace_next_location);
        trace_next_user = (ImageView) traceControlLayout.findViewById(R.id.trace_next_user);
        playIv.setOnClickListener(this);
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

        //rectSelect
        mapRectSelectView.setAlpha(0.3f);
        mapRectSelectView.addRectSelectedCallBack(new MapRectSelectView.RectSelectedCallBack() {
            @Override
            public void rectSelected(Point startPoint, Point endPoint) {
                calcRectLatLng(startPoint, endPoint);
            }
        });
    }

    /**
     * 解析json
     */
    private void parseJson() {
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

    }

    private void painStroke(ArrayList<LatLng> routeList) {
        baiduMap.clear();
        LatLng latLng = null;
        Marker marker = null;
        BitmapDescriptor mMarker = BitmapDescriptorFactory.fromResource(R.drawable.icon_geo);
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
        LatLng pt1 = new LatLng(39.93923, 116.357428);
        LatLng pt2 = new LatLng(39.91923, 116.327428);
        LatLng pt3 = new LatLng(39.89923, 116.347428);
        LatLng pt4 = new LatLng(39.89923, 116.367428);
        LatLng pt5 = new LatLng(39.91923, 116.387428);

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
        OverlayOptions ooPolyline = new PolylineOptions().width(15).color(0xAAFF0000).points(points).customTextureList(customList).textureIndex(index);
//添加到地图
        baiduMap.addOverlay(ooPolyline);
    }

    private DateDialog.Callback startTimeCallback = null;
    private DateDialog.Callback endTimeCallback = null;
    private long startTime = CommonUtil.getCurrentTime();
    private long endTime = CommonUtil.getCurrentTime();
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @Override
    public void onClick(View v) {
//        painStroke(routeList);
      /*  MapMoveUtil map = new MapMoveUtil(bmapView,baiduMap);
        map.moveLooper();*/
        switch (v.getId()) {
            case R.id.trace_view_close:
                traceControlLayout.setVisibility(View.INVISIBLE);
                break;
            case R.id.trace_play://播放
                labels = PALY;
                trace_play.setClickable(false);
                if (observable == null) {
                    observable = Observable.interval(1, 1, TimeUnit.SECONDS);
                }
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
                --num;
                if (num >= 0) {
                    loactionMove(moveMarker);
                }
                break;
            case R.id.trace_next_location://下一位置
                setDisposeClick();
                labels = NEXTSTEP;
                num++;
                if (num <= userLocations.size()) {
                    loactionMove(moveMarker);
                }
                break;
            case R.id.trace_the_previous://上一个用户
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
                        trace_the_previous.setClickable(false);
                        return;
                    }
                    if (k == position - 1) {
                        adpterNodes.get(k).setSelected(true);
                        traceHistoryUserIds.clear();
                        traceHistoryUserIds.add(adpterNodes.get(k).get_id());
                        selected_user.setText(adpterNodes.get(k).getName());
                    } else {
                        adpterNodes.get(k).setSelected(false);
                    }
                }
            gridAdapter.setTraceData(adpterNodes);
            consoleMapPresener.showUserTraceHistory(traceHistoryUserIds, this, startTime, endTime);
            break;
            case R.id.trace_next_user://下一用户
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
                        trace_the_previous.setClickable(false);
                        return;
                    }
                    if (k == posNext + 1) {
                        adpterNodes.get(k).setSelected(true);
                        traceHistoryUserIds.clear();
                        traceHistoryUserIds.add(adpterNodes.get(k).get_id());
                        selected_user.setText(adpterNodes.get(k).getName());
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
        if (textureBmapView != null) {
            textureBmapView.onDestroy();
        }
        compositeDisposable.clear();
        baiduMap.setMyLocationEnabled(false);
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
        baiduMap.setOnMapTouchListener(new myMapTouchListener());
        baiduMap.setOnMapClickListener(new BaiduMap.OnMapClickListener() {

            @Override
            public void onMapClick(LatLng latLng) {
                System.out.print(latLng.latitude);
            }

            @Override
            public boolean onMapPoiClick(MapPoi mapPoi) {
                return false;
            }
        });
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

//    @Override
//    public void onDestroyOptionsMenu() {
//        super.onDestroyOptionsMenu();
//        if(disposable!=null)disposable.dispose();
//    }
//
//    //展示传递过来的用户参数坐标定位
//    @Override
//    public void showUsersLocation(List<LatLng> locations) {
//        baiduMap.clear();
//        LatLng latLng = null;
//        Marker marker = null;
//        List<OverlayOptions> optionList = new ArrayList<OverlayOptions>();
//        BitmapDescriptor mMarker = BitmapDescriptorFactory.fromResource(R.drawable.icon_marka);
//        OverlayOptions options;
//        for (LatLng latLin : locations) {
//            // 经纬度
//            latLng = new LatLng(latLin.latitude, latLin.longitude);
//            // 图标
//            options = new MarkerOptions().position(latLng).icon(mMarker)
//                    .zIndex(5);
//            optionList.add(options);
//            //marker = (Marker) baiduMap.addOverlay(options);
//            //屏蔽代码属于描述maker展示的详情
//            /*Bundle arg0 = new Bundle();
//            arg0.putSerializable("info", latLin);
//            marker.setExtraInfo(arg0);*/
//        }
//        baiduMap.addOverlays(optionList);
////        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(15.0f);
//        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(latLng);
//        baiduMap.setMapStatus(msu);
//        msu = MapStatusUpdateFactory.zoomTo(15.0f);
//        baiduMap.setMapStatus(msu);
//    }

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

    private void calcRectLatLng(Point startPoint, Point endPoint) {
        if (startPoint.x == endPoint.x && startPoint.y == endPoint.y) {
            return;
        }
        LatLng topLeft = baiduMap.getProjection().fromScreenLocation(
                new android.graphics.Point(startPoint.x, startPoint.y));
        LatLng bottomRight = baiduMap.getProjection().fromScreenLocation(
                new android.graphics.Point(endPoint.x, endPoint.y));

        mapRectSelectView.setVisibility(View.INVISIBLE);

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
                            List<LastLocationByUser> oneLineUsersLocList = new ArrayList<LastLocationByUser>();
                            if (rsult.size() == 0) {
                                Toast.makeText(textureBmapView.getContext(),
                                        R.string.tip_rect_select_no_people, Toast.LENGTH_LONG).show();
                                return;
                            }
                            for (UserLocation ul : rsult) {
                                LastLocationByUser llb = new LastLocationByUser(ul.getUserId(),
                                        ul.getLocation().getLatLng(),
                                        "");
                                oneLineUsersLocList.add(llb);
                            }
                            showLocations(oneLineUsersLocList);
                        },
                        err -> {
                            Toast.makeText(textureBmapView.getContext(), err.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                );
    }

    @Override
    public void mapRectSelect() {
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
        if (checkedNodes.size() == 0) {
            trace_the_previous.setClickable(false);
            trace_next_user.setClickable(false);
            trace_play.setClickable(false);
            trace_pause.setClickable(false);
            trace_previous_loaction.setClickable(false);
            trace_next_location.setClickable(false);
        }
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
                trace_the_previous.setClickable(true);
                trace_play.setClickable(true);
                trace_pause.setClickable(true);
                trace_next_user.setClickable(true);
                trace_previous_loaction.setClickable(true);
                trace_next_location.setClickable(true);
                selected_user.setText(node.getName());
                traceHistoryUserIds.add(node.get_id());
                break;
            }
        }
        if (adpterNodes.size() > 0 && !exsitSelected) {
            adpterNodes.get(0).setSelected(true);
            trace_the_previous.setClickable(false);
            trace_next_user.setClickable(true);
            trace_play.setClickable(true);
            trace_pause.setClickable(true);
            trace_previous_loaction.setClickable(true);
            trace_next_location.setClickable(true);
            selected_user.setText(adpterNodes.get(0).getName());
            try {
                selectedNodes.clear();
                selectedNodes.add(adpterNodes.get(0).cloneNode());
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
            traceHistoryUserIds.add(adpterNodes.get(0).get_id());

        }

        gridAdapter.setTraceData(adpterNodes);
    }

    private int num = 0;//轨迹列表数据滚动计数器
    Observable<Long> observable;
    Disposable timerdisposable;
    ArrayList<LatLng> ltns = new ArrayList<>();
    private Overlay myOverlay;
    BitmapDescriptor moveMarker = BitmapDescriptorFactory.fromResource(R.drawable.icon_marka);

    //拿到历史轨迹数据，业务在这里处理
    @Override
    public void showTrackPlayback(List<TraceListItemData> datas) {

//        userLocations.clear();
        if (userLocations.size() > 0 && datas.size() > 0 &&
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
                    /**
                     * this  function do only onece   when subcribe,so  you cant't dispose
                     * @param disposable1
                     */
                    @Override
                    public void onSubscribe(Disposable disposable1) {
                        timerdisposable = disposable1;
                    }

                    @Override
                    public void onNext(Long aLong) {

                        switch (labels) {
                            case PALY:
                                if (num < userLocations.size()) {
                                    loactionMove(moveMarker);
                                    num++;
                                } else {
                                    //当 aLong == listview 适配器数据的count 的时候调用 disabl.dispo()   cancel  this  subscriptpion
                                    if (num == userLocations.size()) {
                                        num = 0;
                                        trace_play.setClickable(true);
                                    }
                                }
                                break;
                            case PAUSE:
                                if (!timerdisposable.isDisposed()) {
                                    timerdisposable.dispose();
                                    timerdisposable = null;
                                }
                                break;
                            case PREVIOUSSTEP://上一步
                                if (num < userLocations.size() && num >= 0) {
                                    --num;
                                    loactionMove(moveMarker);
                                }
                                break;
                            case NEXTSTEP://下一步
                                if (num < userLocations.size()) {
                                    ++num;
                                    loactionMove(moveMarker);
                                }
                                break;
                            case NEXT://上一位
                                break;
                            case LAST://下一位
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
        Toast.makeText(getActivity(), "isChecked" + isChecked, Toast.LENGTH_SHORT).show();

    }


    private List<Node> selectedNodes = new ArrayList<>();

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position > 0) {
            trace_the_previous.setClickable(true);
        } else {
            trace_the_previous.setClickable(false);
        }
        if (position < parent.getAdapter().getCount() - 1) {
            trace_next_user.setClickable(true);
        } else {
            trace_next_user.setClickable(false);
        }
        Node node = (Node) parent.getAdapter().getItem(position);
        selectedNodes.clear();
        traceHistoryUserIds.clear();
        traceHistoryUserIds.add(node.get_id());
        for (Node bean : adpterNodes) {
            if (bean.get_id().equals(node.get_id())) {
                bean.setSelected(true);
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
