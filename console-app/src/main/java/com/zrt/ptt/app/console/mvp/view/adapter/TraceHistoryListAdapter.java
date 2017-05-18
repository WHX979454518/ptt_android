package com.zrt.ptt.app.console.mvp.view.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.xianzhitech.ptt.api.dto.UserLocation;
import com.zrt.ptt.app.console.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by surpass on 2017-5-18.
 */

public class TraceHistoryListAdapter extends BaseAdapter {


    private List<UserLocation> userLocations = new ArrayList<>();
    private Context context;
    private LayoutInflater inflater;

    public TraceHistoryListAdapter(List<UserLocation> userLocations, Context context) {
        this.userLocations = userLocations;
        this.context = context;
        inflater = LayoutInflater.from(context);
    }

    public void setUserLocations(List<UserLocation> userLocations) {
        this.userLocations = userLocations;
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
        UserLocation userLocation = (UserLocation) getItem(position);
        holder.colum.setText(position);
        holder.time.setText(simpleDateFormat.format(userLocation.getLocation().getTime()));
        holder.address.setText(userLocation.getLocation().getRadius()+"");
        holder.speed.setText(userLocation.getLocation().getSpeed());
        holder.latitude.setText(userLocation.getLocation().getLatLng().getLat()+"");
        holder.longitude.setText(userLocation.getLocation().getLatLng().getLng()+"");
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
