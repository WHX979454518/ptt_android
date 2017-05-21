package com.zrt.ptt.app.console.mvp.presenter;

import com.baidu.mapapi.model.LatLng;
import com.xianzhitech.ptt.api.dto.UserLocation;
import com.zrt.ptt.app.console.mvp.bean.TraceListItemData;
import com.zrt.ptt.app.console.mvp.model.Imodel.IConsoleMap;
import com.zrt.ptt.app.console.mvp.model.ModelImp.ConsoleMapModel;
import com.zrt.ptt.app.console.mvp.view.IView.IConsoMapView;
import com.zrt.ptt.app.console.mvp.view.fragment.ConsoleMapFragment;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-18.
 */

public class ConsoleMapPresener {
    private IConsoMapView iConsoMapView;
    private IConsoleMap iConsoleMap;

    public ConsoleMapPresener(IConsoMapView iConsoMapView) {
        this.iConsoMapView = iConsoMapView;
        this.iConsoleMap = new ConsoleMapModel();
    }

    public void showUserTraceHistory(List<String> traceHistoryUserIds, IConsoMapView ImapView, Long startTime, long endTime) {
        iConsoleMap.getUserTraceHistory(new IConsoleMap.CallBackTraceHistory() {
            @Override
            public void callBackTraceDatas(List<UserLocation> userLocations) {
                List<TraceListItemData> datas = new ArrayList<TraceListItemData>();
                for (UserLocation ulocation : userLocations) {
                    datas.add(new TraceListItemData(ulocation.getLocation().getTime(),
                            ulocation.getLocation().getSpeed() + "km/h",
                            ulocation.getLocation().getRadius() + "",
                            new LatLng(ulocation.getLocation().getLatLng().getLat(),
                                    ulocation.getLocation().getLatLng().getLng()),
                            ulocation.getUserId()));
                }
                iConsoMapView.showTrackPlayback(datas);
            }

            @Override
            public void callBackDisposable(Disposable disposable) {
                iConsoMapView.callBackiDisposable(disposable);
            }
        }, traceHistoryUserIds, startTime, endTime);
    }
}
