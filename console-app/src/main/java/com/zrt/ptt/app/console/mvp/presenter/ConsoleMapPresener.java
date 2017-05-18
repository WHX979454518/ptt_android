package com.zrt.ptt.app.console.mvp.presenter;

import com.xianzhitech.ptt.api.dto.UserLocation;
import com.zrt.ptt.app.console.mvp.model.Imodel.IConsoleMap;
import com.zrt.ptt.app.console.mvp.model.ModelImp.ConsoleMapModel;
import com.zrt.ptt.app.console.mvp.view.IView.IConsoMapView;
import com.zrt.ptt.app.console.mvp.view.fragment.ConsoleMapFragment;

import java.util.List;

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

    public void showUserTraceHistory(List<String> traceHistoryUserIds, IConsoMapView ImapView,Long startTime, long endTime) {
        iConsoleMap.getUserTraceHistory(new IConsoleMap.CallBackTraceHistory() {
            @Override
            public void callBackTraceDatas(List<UserLocation> userLocations) {
                iConsoMapView.showTrackPlayback(userLocations);
            }
        }, traceHistoryUserIds, startTime, endTime);
    }
}
