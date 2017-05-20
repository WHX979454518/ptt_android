package com.zrt.ptt.app.console.mvp.view.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.xianzhitech.ptt.api.dto.UserLocation;
import com.zrt.ptt.app.console.R;
import com.zrt.ptt.app.console.mvp.bean.TraceListItemData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by surpass on 2017-5-18.
 */

public class TraceHistoryListAdapter extends BaseAdapter {


    private List<TraceListItemData>  userLocations = new ArrayList<>();
    private Context context;
    private LayoutInflater inflater;

    public TraceHistoryListAdapter(List<TraceListItemData>  userLocations, Context context) {
        this.userLocations = userLocations;
        this.context = context;
        inflater = LayoutInflater.from(context);
    }

    public void setUserLocations(List<TraceListItemData> datas) {
        this.userLocations = datas;
        notifyDataSetChanged();
    }
    @Override
    public int getCount() {
        return userLocations.size();
    }

    @Override
    public Object getItem(int position) {
        return userLocations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if(convertView==null){
            convertView =inflater.inflate(R.layout.trace_listview_item_ly,null);
            holder=new Holder();
            holder.colum=(TextView) convertView.findViewById(R.id.trace_listitem_num);
            holder.time=(TextView) convertView.findViewById(R.id.trace_location_time);
            holder.address=(TextView) convertView.findViewById(R.id.trace_lcation_address);
            holder.speed=(TextView) convertView.findViewById(R.id.trace_location_speed);
            holder.latitude=(TextView) convertView.findViewById(R.id.trace_location_latitude);
            holder.longitude=(TextView) convertView.findViewById(R.id.trace_lcation_longitude);
            convertView.setTag(holder);
        }else{
            holder=(Holder) convertView.getTag();
        }
        TraceListItemData userLocation = (TraceListItemData) getItem(position);
        holder.colum.setText(position+"");
        holder.time.setText(simpleDateFormat.format(userLocation.getTime()));
        holder.address.setText(userLocation.getAddres());
        holder.speed.setText(userLocation.getSpeed());
        holder.latitude.setText(userLocation.getLatitude());
        holder.longitude.setText(userLocation.getLongitude());
        return convertView;
    }

    class Holder{
        TextView colum;
        TextView time;
        TextView address;
        TextView speed;
        TextView latitude;
        TextView longitude;
    }
}
