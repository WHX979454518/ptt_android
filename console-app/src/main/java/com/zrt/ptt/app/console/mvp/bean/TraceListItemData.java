package com.zrt.ptt.app.console.mvp.bean;

import com.baidu.mapapi.model.LatLng;
import com.zrt.ptt.app.console.mvp.model.Node;

/**
 * Created by surpass on 2017-5-20.
 */

public class TraceListItemData implements Cloneable{
    private long time;
    private String speed;
    private String addres;
    private String latitude;
    private String longitude;
    private LatLng latLng;

    public LatLng getLatLng() {
        return latLng;
    }

    public void setLatLng(LatLng latLng) {
        this.latLng = latLng;
    }

    private String name;
    private boolean current = false;//播放当强前行默认false


    public TraceListItemData(long time, String speed, String addres, LatLng latLng,String name) {
        this.time = time;
        this.speed = speed;
        this.addres = addres;
        this.latLng = latLng;
        this.name = name;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        TraceListItemData clone = null;
        try{
            clone = (TraceListItemData) super.clone();
        }catch(CloneNotSupportedException e){
            throw new RuntimeException(e);  // won't happen
        }
        return clone;
    }

    public TraceListItemData cloneNode() throws CloneNotSupportedException{
        return (TraceListItemData) clone();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getSpeed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

    public String getAddres() {
        return addres;
    }

    public void setAddres(String addres) {
        this.addres = addres;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

}
