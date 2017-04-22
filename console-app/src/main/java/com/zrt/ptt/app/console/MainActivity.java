package com.zrt.ptt.app.console;

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.baidu.mapapi.map.MapView;
import com.zrt.ptt.app.console.mvp.view.fragment.OrganizationFragment;
import com.zrt.ptt.app.console.mvp.view.fragment.SystemStateFragment;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.pupmenu)
    ImageView pupmenu;
    @BindView(R.id.all_call)
    TextView allCall;
    @BindView(R.id.sign_out)
    TextView signOut;
    @BindView(R.id.time_hms)
    TextView timeHms;
    @BindView(R.id.console)
    TextView console;
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
    RelativeLayout contactsContainer;
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
    private MapView mMapView;
    private OrganizationFragment organizationFragment;
    private SystemStateFragment stateFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//remove title bar 即隐藏标题栏
        getSupportActionBar().hide();// 隐藏ActionBar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//remove notification bar 即全屏
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mMapView = (MapView) findViewById(R.id.bmapView);
        View view = getLayoutInflater().inflate(R.layout.organiz_function_btn_ly,null);
        setSelected(rb1.getId());
    }

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
        }
        ft.commitAllowingStateLoss();
    }

    public void checkRadio(int id) {
        switch (id) {
            case R.id.rb1:
                rb1.setChecked(true);
                rb1.setBackgroundColor(getResources().getColor(R.color.btn_pre_red));
                rb2.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb3.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb2.setChecked(false);
                rb3.setChecked(false);
                break;
            case R.id.rb2:
                rb2.setChecked(true);
                rb1.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb2.setBackgroundColor(getResources().getColor(R.color.btn_pre_red));
                rb3.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb1.setChecked(false);
                rb3.setChecked(false);
                break;
            case R.id.rb3:
                rb3.setChecked(true);
                rb1.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb2.setBackgroundColor(getResources().getColor(R.color.btn_nopre_red));
                rb3.setBackgroundColor(getResources().getColor(R.color.btn_pre_red));
                rb1.setChecked(false);
                rb2.setChecked(false);
                break;
        }
    }

    private void hideFragment(FragmentTransaction ft, int id) {
        if (organizationFragment != null && id != rb1.getId())
            ft.hide(organizationFragment);
        if (stateFragment != null && id != rb2.getId())
            ft.hide(stateFragment);
//        if (managFinacFrag != null && id != rb3.getId())
//            ft.hide(managFinacFrag);

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

    @OnClick({R.id.pupmenu, R.id.all_call, R.id.sign_out, R.id.rb1, R.id.rb2, R.id.rb3, R.id.contacts_container, R.id.bmapView})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.pupmenu:
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
}
