package com.zrt.ptt.app.console.mvp.model.Imodel;

import com.xianzhitech.ptt.api.dto.UserLocation;

import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * Created by surpass on 2017-5-18.
 */

public interface IConsoleMap {
    void getUserTraceHistory(CallBackTraceHistory callback,List<String> traceHistoryUserIds,Long startTime, long endTime);
    interface CallBackTraceHistory{
        void callBackTraceDatas(List<UserLocation> userLocations);
        void callBackDisposable(Disposable disposable);
    }
}
