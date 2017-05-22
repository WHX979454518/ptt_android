package com.zrt.ptt.app.console.mvp.view.activity;

import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
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
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.model.LatLng;
import com.xianzhitech.ptt.api.dto.LastLocationByUser;
import com.xianzhitech.ptt.broker.RoomMode;
import com.xianzhitech.ptt.ui.base.BaseActivity;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.Node;
import com.zrt.ptt.app.console.mvp.presenter.MainActivityPresenter;
import com.zrt.ptt.app.console.mvp.view.IView.IConsoMapView;
import com.zrt.ptt.app.console.mvp.view.IView.IMainActivityView;
import com.zrt.ptt.app.console.mvp.view.fragment.ConsoleMapFragment;
import com.zrt.ptt.app.console.mvp.view.fragment.OrganizationFragment;
import com.zrt.ptt.app.console.mvp.view.fragment.SystemStateFragment;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import utils.CommonUtil;
import utils.LogUtils;

public class MainActivity extends BaseActivity implements View.OnClickListener, IMainActivityView {
    private final static String TAG = "MainActivity";

    @BindView(R.id.pupmenu)
    ImageView pupmenu;
    @BindView(R.id.frame_map)
    TextView frame_map;
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
    @BindView(R.id.map_container)
    FrameLayout mapContainer;
    @BindView(R.id.main_content)
    LinearLayout mainContent;
    private MapView mMapView;
    private OrganizationFragment organizationFragment;
    private SystemStateFragment stateFragment;
    private ConsoleMapFragment consoleMapFragment;
    private PopupWindow popupWindow;
    private View rootView;
    private LinearLayout logRecord;
    private LinearLayout playBack;
    private ImageView userLocation;
    private MainActivityPresenter mainPresenter = new MainActivityPresenter(this);
    private Timer timer;
    private TimerTask timerTask;
    private TextView time_hms,time_ymd;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//remove title bar 即隐藏标题栏
        getSupportActionBar().hide();// 隐藏ActionBar
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//remove notification bar 即全屏
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initView();
//        mainPresenter.UpDataOrganzation();
    }

    private void initView() {
        View view = getLayoutInflater().inflate(R.layout.main_title_layout, null);
        time_hms = (TextView) findViewById(R.id.time_hms);
        time_ymd = (TextView) findViewById(R.id.time_ymd);
        updateSystemTime();
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

    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private Disposable timeDisposable;
    private void updateSystemTime(){
        timeDisposable =  Observable.interval(1,1,TimeUnit.SECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Consumer<Long>() {
            @Override
            public void accept(@NonNull Long aLong) throws Exception {
                SimpleDateFormat dff = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                dff.setTimeZone(TimeZone.getTimeZone("GMT"));
                String s = dff.format(new Date());
                String year = s.substring(0,s.indexOf(" "));
                String time = s.substring(s.indexOf(" ")+1);
                time_hms.setText(time);
                time_ymd.setText(year);
            }
        });
        compositeDisposable.add(timeDisposable);
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

                if (consoleMapFragment == null) {
                    consoleMapFragment = new ConsoleMapFragment();
                    ft.add(R.id.map_container, consoleMapFragment);
                } else
                    ft.show(consoleMapFragment);

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
        if(compositeDisposable != null) {
            compositeDisposable.clear();
        }
//        if(timeDisposable!=null)timeDisposable.dispose();
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * butterknife绑定点击监听事件
     *
     * @param view
     */
    @OnClick({R.id.pupmenu, R.id.all_call, R.id.sign_out, R.id.contacts_container, R.id.frame_map})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.frame_map:
            {
                IConsoMapView imapView = (ConsoleMapFragment)getSupportFragmentManager().
                        findFragmentById(R.id.map_container);
                imapView.mapRectSelect();
            }
                break;
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
                finish();
                break;
            case R.id.contacts_container:
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

    @Override
    public void showLocations(List<LastLocationByUser> lastLocationByUsers) {
        IConsoMapView imapView = (ConsoleMapFragment)getSupportFragmentManager().findFragmentById(R.id.map_container);
        imapView.showLocations(lastLocationByUsers);
    }

    @Override
    public void showChatRoomView(List<String> userIds, List<String> groupIds, RoomMode roomMode) {
        SystemStateFragment systemStateFragment = (SystemStateFragment)getSupportFragmentManager().findFragmentById(R.id.system_state_container);
        systemStateFragment.showChatRoomView(userIds, groupIds, roomMode);
    }

    @Override
    public void showHistorytraceDialog() {
        IConsoMapView imapView = (ConsoleMapFragment)getSupportFragmentManager().findFragmentById(R.id.map_container);
        imapView.showHistoryDialog();
    }

    //拿到mapfragment的轨迹控制界面显示隐藏
    @Override
    public int getLayoutVisible() {
        IConsoMapView imapView = (ConsoleMapFragment)getSupportFragmentManager().findFragmentById(R.id.map_container);
        return imapView.getLayoutVisibility();
    }

    @Override
    public void sendCheckedUsers(List<Node> checkedNodes) {
        IConsoMapView imapView = (ConsoleMapFragment)getSupportFragmentManager().findFragmentById(R.id.map_container);
        imapView.sendCheckedUsers(checkedNodes);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////Override methods from BaseActivity /////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void joinRoomConfirmed(@NotNull String roomId, boolean fromInvitation, RoomMode roomMode) {
//        super.joinRoomConfirmed(roomId, fromInvitation, isVideoChat);
        LogUtils.d(TAG, "joinRoomConfirmed() called with: roomId = [" + roomId + "], fromInvitation = [" + fromInvitation + "], roomMode = [" + roomMode + "]");

        SystemStateFragment systemStateFragment = (SystemStateFragment)getSupportFragmentManager().findFragmentById(R.id.system_state_container);
        systemStateFragment.showChatRoomView(roomId, fromInvitation, roomMode);
    }
}
