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
import com.zrt.ptt.app.console.mvp.model.Node;
import com.zrt.ptt.app.console.mvp.model.SysStateNodeBean;
import com.zrt.ptt.app.console.mvp.model.SysStateNodeBean;
import com.zrt.ptt.app.console.mvp.view.adapter.MyTreeListViewAdapter;
import com.zrt.ptt.app.console.mvp.view.adapter.TreeListViewAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class SystemStateFragment extends Fragment {
    private ListView treeLv;
    private Button checkSwitchBtn;
    private MyTreeListViewAdapter<SysStateNodeBean> adapter;
    private List<SysStateNodeBean> mDatas = new ArrayList<SysStateNodeBean>();
    //标记是显示Checkbox还是隐藏
    private boolean isHide = false;
    private View view;

    public SystemStateFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_system_state, container, false);

        super.onCreate(savedInstanceState);

        initDatas();
        treeLv = (ListView) view.findViewById(R.id.sys_state_tree_lv);

        try {
            adapter = new MyTreeListViewAdapter<SysStateNodeBean>(treeLv, getActivity(),
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
        mDatas.add(new SysStateNodeBean(1, 0, "中国古代"));
        mDatas.add(new SysStateNodeBean(2, 1, "唐朝"));
        mDatas.add(new SysStateNodeBean(3, 1, "宋朝"));
        mDatas.add(new SysStateNodeBean(4, 1, "明朝"));
        mDatas.add(new SysStateNodeBean(5, 2, "李世民"));
        mDatas.add(new SysStateNodeBean(6, 2, "李白"));

        mDatas.add(new SysStateNodeBean(7, 3, "赵匡胤"));
        mDatas.add(new SysStateNodeBean(8, 3, "苏轼"));

        mDatas.add(new SysStateNodeBean(9, 4, "朱元璋"));
        mDatas.add(new SysStateNodeBean(10, 4, "唐伯虎"));
        mDatas.add(new SysStateNodeBean(11, 4, "文征明"));
        mDatas.add(new SysStateNodeBean(12, 7, "赵建立"));
        mDatas.add(new SysStateNodeBean(13, 8, "苏东东"));
        mDatas.add(new SysStateNodeBean(14, 10, "秋香"));
        mDatas.add(new SysStateNodeBean(15, 0, "美国古代"));
        mDatas.add(new SysStateNodeBean(16, 15, "美国西部世界"));
        mDatas.add(new SysStateNodeBean(17, 15, "美国东世界"));
        mDatas.add(new SysStateNodeBean(18, 16, "西部世界华盛顿"));
        mDatas.add(new SysStateNodeBean(19, 16, "西部世界芝加哥"));
        mDatas.add(new SysStateNodeBean(20, 17, "东部世界洛杉矶"));
        mDatas.add(new SysStateNodeBean(21, 17, "东部世界纽约"));
    }

}
