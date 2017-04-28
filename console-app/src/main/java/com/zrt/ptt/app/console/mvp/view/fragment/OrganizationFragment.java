package com.zrt.ptt.app.console.mvp.view.fragment;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.data.ContactDepartment;
import com.xianzhitech.ptt.data.ContactEnterprise;
import com.xianzhitech.ptt.data.ContactUser;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.OrgNodeBean;
import com.zrt.ptt.app.console.mvp.model.Node;
import com.zrt.ptt.app.console.mvp.view.adapter.MyTreeListViewAdapter;
import com.zrt.ptt.app.console.mvp.view.adapter.TreeListViewAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;


/**
 * A simple {@link Fragment} subclass.
 */


public class OrganizationFragment extends Fragment {

    private ListView treeLv;
    private MyTreeListViewAdapter<OrgNodeBean> adapter;
    private List<OrgNodeBean> mDatas = new ArrayList<OrgNodeBean>();
    //标记是显示Checkbox还是隐藏
    private boolean isHide = false;
    private View view;

    public OrganizationFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_organization, container, false);

        super.onCreate(savedInstanceState);

        AppComponent appComponent = (AppComponent) App.getInstance().getApplicationContext();

        Observable<ContactEnterprise> contactEnterprise = appComponent.getSignalBroker().getEnterprise();

        contactEnterprise.observeOn(AndroidSchedulers.mainThread()).subscribe(contactEnterprise1 -> {
            List<ContactDepartment> contactDepartment = contactEnterprise1.getDepartments();
            OrgNodeBean org ;

            for (ContactDepartment department :contactDepartment){
                org = new OrgNodeBean(department.getId(),department.getParentObjectId(),department.getName());
                mDatas.add(org);

            }
            List<ContactUser> contactUser = contactEnterprise1.getDirectUsers();

            for (ContactUser user:contactUser){
                org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                mDatas.add(org);
            }
        });

       /* contactEnterprise.observeOn(AndroidSchedulers.mainThread()).subscribe(enterprise->{

           List<ContactDepartment> contactDepartment = enterprise.getDepartments();
           OrgNodeBean org ;

            for (ContactDepartment department :contactDepartment){
               org = new OrgNodeBean(department.getId(),department.getParentObjectId(),department.getName());
               mDatas.add(org);

           }
           List<ContactUser> contactUser = enterprise.getDirectUsers();

            for (ContactUser user:contactUser){
               org = new OrgNodeBean(user.getId(),user.getParentObjectId(),user.getName());
                mDatas.add(org);
           }
       });*/
//                 = contactEnterprise.observeOn(AndroidSchedulers.mainThread())    .subscribe(enterprise -> {        // do something with enterprise    })

//        initDatas();
        treeLv = (ListView) view.findViewById(R.id.tree_lv);

        try {
            adapter = new MyTreeListViewAdapter<OrgNodeBean>(treeLv, getActivity(),
                    mDatas, 0, isHide);

            adapter.setOnTreeNodeClickListener(new TreeListViewAdapter.OnTreeNodeClickListener() {
                @Override
                public void onClick(Node node, int position) {
                    if (node.isLeaf()) {
                        Toast.makeText(getActivity().getApplicationContext(), node.getName(),
                                Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCheckChange(Node node, int position,
                                          List<Node> checkedNodes) {
                    // TODO Auto-generated method stub

                    StringBuffer sb = new StringBuffer();
                    for (Node n : checkedNodes) {
                        /*int pos = n.get_Id() - 1;
                        sb.append(mDatas.get(pos).getName()).append("---")
                                .append(pos + 1).append(";");*/

                    }

                    /*Toast.makeText(getActivity().getApplicationContext(), sb.toString(),
                            Toast.LENGTH_SHORT).show();*/
                }

            });
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        treeLv.setAdapter(adapter);


        return view;

    }



    @org.junit.Test
    public void initDatas() {
//        mDatas.add(new OrgNodeBean(1, 0, "中国古代"));
//        mDatas.add(new OrgNodeBean(2, 1, "唐朝"));
//        mDatas.add(new OrgNodeBean(3, 1, "宋朝"));
//        mDatas.add(new OrgNodeBean(4, 1, "明朝"));
//        mDatas.add(new OrgNodeBean(5, 2, "李世民"));
//        mDatas.add(new OrgNodeBean(6, 2, "李白"));
//
//        mDatas.add(new OrgNodeBean(7, 3, "赵匡胤"));
//        mDatas.add(new OrgNodeBean(8, 3, "苏轼"));
//
//        mDatas.add(new OrgNodeBean(9, 4, "朱元璋"));
//        mDatas.add(new OrgNodeBean(10, 4, "唐伯虎"));
//        mDatas.add(new OrgNodeBean(11, 4, "文征明"));
//        mDatas.add(new OrgNodeBean(12, 7, "赵建立"));
//        mDatas.add(new OrgNodeBean(13, 8, "苏东东"));
//        mDatas.add(new OrgNodeBean(14, 10, "秋香"));
        String json = "{\"status\":200,\"data\":[{\"lastLocationTimeCST\":\"\",\"positionname\":\"总经理\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jcogiAYgUMAABsrdgKnKM450.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"58fec69ff3ca2d0007beb0e7\",\"commander\":1,\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"phoneNbr\":11122233344,\"idNumber\":9100001,\"name\":\"指挥号\",\"__v\":0,\"position\":\"59014861f3ca2d0007beb151\",\"father\":\"58fec8a1f3ca2d0007beb0eb\",\"mail\":\"\",\"privileges\":{\"priority\":null,\"viewMap\":false,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"组长\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jco4GAAfhgAABrtMxgZOY283.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"58fec91df3ca2d0007beb0ec\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"五杀\",\"father\":\"58fec8a1f3ca2d0007beb0eb\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"asd@dom.com\",\"idNumber\":9100002,\"phoneNbr\":13500000000,\"__v\":0,\"position\":\"59014876f3ca2d0007beb152\",\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"组长\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jco4GAAfhgAABrtMxgZOY283.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"5901491ff3ca2d0007beb156\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"流浪汉\",\"father\":\"59014806f3ca2d0007beb150\",\"position\":\"59014876f3ca2d0007beb152\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"aaad@dom.com\",\"idNumber\":9100003,\"phoneNbr\":13566666666,\"__v\":0,\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"lastLocationTimeCST\":\"\",\"positionname\":\"平民\",\"locateicon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jcoqaAXNIPAABq9HszdDo314.png_24_24\",\"icon\":\"http://netptt.cn:19999/img/group1/M00/00/00/eSkWC1jbb1SAS_t4AACAR6D-mGo442.png_16_16\",\"_id\":\"5901495af3ca2d0007beb157\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"打手\",\"father\":\"59014806f3ca2d0007beb150\",\"position\":\"590148a2f3ca2d0007beb154\",\"password\":\"670b14728ad9902aecba32e22fa4f6bd\",\"mail\":\"adf@dom.com\",\"idNumber\":9100004,\"phoneNbr\":13522222222,\"__v\":0,\"privileges\":{\"priority\":3,\"viewMap\":true,\"muteAble\":true,\"powerInviteAble\":true,\"calledOuterAble\":true,\"callOuterAble\":true,\"forbidSpeak\":true,\"joinAble\":true,\"calledAble\":true,\"groupAble\":true,\"callAble\":true}},{\"_id\":\"58fec8a1f3ca2d0007beb0eb\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true,\"father\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"青铜\",\"__v\":0},{\"_id\":\"59014806f3ca2d0007beb150\",\"enterprise\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true,\"father\":\"58fec69ff3ca2d0007beb0e6\",\"name\":\"撸撸撸\",\"__v\":0},{\"father\":-1,\"name\":\"czqtest\",\"_id\":\"58fec69ff3ca2d0007beb0e6\",\"isParent\":true}]}";

        Gson gson = new Gson();
        JSONObject data = null;
        try {
            data = new JSONObject(json);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(data.has("data")){
            try {
                String datas = data.getString("data");
                List<OrgNodeBean> goodsLists = gson.fromJson(datas, new TypeToken<List<OrgNodeBean>>() {
                }.getType());
                for(OrgNodeBean user:goodsLists) {
                    mDatas.add(user);
                    System.out.printf(user.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

}
