package com.zrt.ptt.app.console.mvp.view.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.Node;


import java.util.List;




/**
 * Created by surpass on 2017-4-20.
 */

public class MyTreeListViewAdapter<T> extends TreeListViewAdapter<T> {




    public MyTreeListViewAdapter(ListView mTree, Context context,
                                 List<T> datas, int defaultExpandLevel,boolean isHide)
            throws IllegalArgumentException, IllegalAccessException {
        super(mTree, context, datas, defaultExpandLevel,isHide);
    }

    @SuppressWarnings("unchecked")
    @Override
    public View getConvertView(Node node, int position, View convertView,
                               ViewGroup parent) {
        ViewHolder viewHolder = null;
        if (convertView == null)
        {
            convertView = mInflater.inflate(R.layout.list_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.icon = (ImageView) convertView
                    .findViewById(R.id.id_treenode_icon);
            viewHolder.label = (TextView) convertView
                    .findViewById(R.id.id_treenode_name);
            viewHolder.head = (ImageView) convertView.findViewById(R.id.id_head_icon);
            viewHolder.checkBox = (CheckBox)convertView.findViewById(R.id.id_treeNode_check);

            convertView.setTag(viewHolder);

        } else
        {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (node.getIcon() == -1)
        {
            viewHolder.icon.setVisibility(View.INVISIBLE);
        } else
        {
            viewHolder.icon.setVisibility(View.VISIBLE);
            viewHolder.icon.setImageResource(node.getIcon());
        }

        //是否是叶子节点不是就关闭头像
        if(node.isLeaf()){
            viewHolder.head.setVisibility(View.VISIBLE);
        }else {
            viewHolder.head.setVisibility(View.GONE);
        }

        if(node.isOnline()){
            viewHolder.label.setTextColor(mContext.getResources().getColor(R.color.title_bg_red));
        }else {
            viewHolder.label.setTextColor(mContext.getResources().getColor(R.color.dark_grey));
        }
        if(node.isHideChecked()){
            viewHolder.checkBox.setVisibility(View.GONE);
        }else{
            viewHolder.checkBox.setVisibility(View.VISIBLE);
            setCheckBoxBg(viewHolder.checkBox,node.isChecked());
        }
        viewHolder.label.setText(node.getName());



        return convertView;
    }
    private final class ViewHolder
    {
        ImageView icon;
        ImageView head;
        TextView label;
        CheckBox checkBox;
    }

    /**
     * checkbox是否显示
     * @param cb
     * @param isChecked
     */
    private void setCheckBoxBg(CheckBox cb,boolean isChecked){
        if(isChecked){
            cb.setBackgroundResource(R.drawable.selected);
        }else{
            cb.setBackgroundResource(R.drawable.selectedno);
        }
    }

}
