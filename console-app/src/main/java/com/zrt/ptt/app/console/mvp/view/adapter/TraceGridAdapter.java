package com.zrt.ptt.app.console.mvp.view.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.model.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by surpass on 2017-5-16.
 */

public class TraceGridAdapter extends BaseAdapter {
    private List<Node> traceData ;
    private Context context;
    private LayoutInflater inflater;

    public void setTraceData(List<Node> traceData) {
        if(traceData!=null){
            traceData.clear();
        }
        this.traceData = traceData;
        notifyDataSetChanged();
    }
    public void addItemUser(Node node){
        if(traceData!=null){
            if(!traceData.contains(node)){
                traceData.add(node);
            }

        }
        notifyDataSetChanged();
    }

    public TraceGridAdapter(List<Node> traceData,Context context ) {
        this.traceData = traceData;
        this.context = context;
        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return traceData.size();
    }

    @Override
    public Object getItem(int position) {
        return traceData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if(convertView==null){
            convertView =inflater.inflate(R.layout.trace_grid_item,null);
            holder=new Holder();
            holder.user=(TextView) convertView.findViewById(R.id.trace_grid_item_txt);
            convertView.setTag(holder);
        }else{
            holder=(Holder) convertView.getTag();
        }
        holder.user.setText(traceData.get(position).getName());

        return convertView;
    }

    class Holder {
        TextView user;
    }
}
