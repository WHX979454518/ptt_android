package com.zrt.ptt.app.console.mvp.bean;

/**
 * Created by surpass on 2017-5-20.
 */

public class TraceListItemData {
    private long time;
    private String speed;
    private String addres;
    private String latitude;
    private String longitude;
    private boolean current = false;//播放当强前行默认false


    public TraceListItemData(long time, String speed, String addres, String latitude, String longitude) {
        this.time = time;
        this.speed = speed;
        this.addres = addres;
        this.latitude = latitude;
        this.longitude = longitude;
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
