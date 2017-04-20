package com.zrt.ptt.app.console.mvp.view.fragment;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.MyNodeBean;
import com.zrt.ptt.app.console.mvp.model.Node;
import com.zrt.ptt.app.console.mvp.view.adapter.MyTreeListViewAdapter;
import com.zrt.ptt.app.console.mvp.view.adapter.TreeListViewAdapter;

import java.util.ArrayList;
import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 */


public class OrganizationFragment extends Fragment {

    private ListView treeLv;
    private Button checkSwitchBtn;
    private MyTreeListViewAdapter<MyNodeBean> adapter;
    private List<MyNodeBean> mDatas = new ArrayList<MyNodeBean>();
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
        initDatas();
        treeLv = (ListView) view.findViewById(R.id.tree_lv);
//        checkSwitchBtn = (Button)view.findViewById(R.id.check_switch_btn);

//        checkSwitchBtn.setOnClickListener(new View.OnClickListener(){
//
//            @Override
//            public void onClick(View v) {
//                if(isHide){
//                    isHide = false;
//                }else{
//                    isHide = true;
//                }
//
//                adapter.updateView(isHide);
//            }
//
//        });
        try {
            adapter = new MyTreeListViewAdapter<MyNodeBean>(treeLv, getActivity(),
                    mDatas, 10, isHide);

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
                        int pos = n.getId() - 1;
                        sb.append(mDatas.get(pos).getName()).append("---")
                                .append(pos + 1).append(";");

                    }

                    Toast.makeText(getActivity().getApplicationContext(), sb.toString(),
                            Toast.LENGTH_SHORT).show();
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

    private void initDatas() {
        mDatas.add(new MyNodeBean(1, 0, "中国古代"));
        mDatas.add(new MyNodeBean(2, 1, "唐朝"));
        mDatas.add(new MyNodeBean(3, 1, "宋朝"));
        mDatas.add(new MyNodeBean(4, 1, "明朝"));
        mDatas.add(new MyNodeBean(5, 2, "李世民"));
        mDatas.add(new MyNodeBean(6, 2, "李白"));

        mDatas.add(new MyNodeBean(7, 3, "赵匡胤"));
        mDatas.add(new MyNodeBean(8, 3, "苏轼"));

        mDatas.add(new MyNodeBean(9, 4, "朱元璋"));
        mDatas.add(new MyNodeBean(10, 4, "唐伯虎"));
        mDatas.add(new MyNodeBean(11, 4, "文征明"));
        mDatas.add(new MyNodeBean(12, 7, "赵建立"));
        mDatas.add(new MyNodeBean(13, 8, "苏东东"));
        mDatas.add(new MyNodeBean(14, 10, "秋香"));
    }

}
