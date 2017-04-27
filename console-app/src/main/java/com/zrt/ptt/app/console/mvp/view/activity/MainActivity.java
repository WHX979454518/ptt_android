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
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mapapi.map.MapView;
import com.xianzhitech.ptt.ui.base.BaseActivity;
import com.xianzhitech.ptt.ui.roomlist.RoomListFragment;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.presenter.MainActivityPresenter;
import com.zrt.ptt.app.console.mvp.view.IView.IMainActivityView;
import com.zrt.ptt.app.console.mvp.view.fragment.OrganizationFragment;
import com.zrt.ptt.app.console.mvp.view.fragment.SystemStateFragment;

import org.json.JSONObject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends BaseActivity implements View.OnClickListener,IMainActivityView{
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
    @BindView(R.id.rb1)
    RadioButton rb1;
    @BindView(R.id.rb2)
    RadioButton rb2;
    @BindView(R.id.rb3)
    RadioButton rb3;
    @BindView(R.id.contacts_container)
    LinearLayout contactsContainer;
    @BindView(R.id.bmapView)
    MapView bmapView;
    @BindView(R.id.map_container)
    FrameLayout mapContainer;
    @BindView(R.id.main_content)
    LinearLayout mainContent;
    @BindView(R.id.linear_check_lay)
    LinearLayout linearCheckLay;
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
    private MainActivityPresenter mainPresenter = new MainActivityPresenter(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//remove title bar 即隐藏标题栏
        getSupportActionBar().hide();// 隐藏ActionBar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//remove notification bar 即全屏
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mMapView = (MapView) findViewById(R.id.bmapView);
        View view = getLayoutInflater().inflate(R.layout.organiz_function_btn_ly, null);
        setSelected(rb1.getId());
        initView();
        mainPresenter.UpDataOrganzation();
    }

    private void initView() {
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
     * @param id
     */
    public void setSelected(int id) {
        checkRadio(id);
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        hideFragment(ft, id);
        switch (id) {
            case R.id.rb1:
                if (organizationFragment == null) {
                    organizationFragment = new OrganizationFragment();
                    ft.add(R.id.contacts_container, organizationFragment);
                } else if (!organizationFragment.isVisible())
                    ft.show(organizationFragment);
                break;
            case R.id.rb2:
                if (stateFragment == null) {
                    stateFragment = new SystemStateFragment();
                    ft.add(R.id.contacts_container, stateFragment);
                } else if (!stateFragment.isVisible())
                    ft.show(stateFragment);
                break;
            case R.id.rb3:
                if (roomListFragment == null) {
                    roomListFragment = new RoomListFragment();
                    ft.add(R.id.contacts_container, roomListFragment);
                } else if (!roomListFragment.isVisible())
                    ft.show(roomListFragment);
                break;
        }
        ft.commitAllowingStateLoss();
    }

    /**
     * 左侧切换钮背景等相关处理
     *
     * @param id
     */
    public void checkRadio(int id) {
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
    }

    /**
     * 隐藏fragment
     *
     * @param ft
     * @param id
     */
    private void hideFragment(FragmentTransaction ft, int id) {
        if (organizationFragment != null && id != rb1.getId())
            ft.hide(organizationFragment);
        if (stateFragment != null && id != rb2.getId())
            ft.hide(stateFragment);
        if (roomListFragment != null && id != rb3.getId())
            ft.hide(roomListFragment);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mMapView.onDestroy();
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
    @OnClick({R.id.pupmenu, R.id.all_call, R.id.sign_out, R.id.rb1, R.id.rb2, R.id.rb3, R.id.contacts_container, R.id.bmapView})
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
            case R.id.rb1:
                setSelected(rb1.getId());
                break;
            case R.id.rb2:
                setSelected(rb2.getId());
                break;
            case R.id.rb3:
                setSelected(rb3.getId());
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
        Toast.makeText(this,"执行了",Toast.LENGTH_LONG).show();
        Log.e("Organization" , data.toString());
    }
}
