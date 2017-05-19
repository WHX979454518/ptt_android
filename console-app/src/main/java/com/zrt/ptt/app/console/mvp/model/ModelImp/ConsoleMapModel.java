package com.zrt.ptt.app.console.mvp.model.ModelImp;

import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.api.dto.UserLocation;
import com.zrt.ptt.app.console.App;
import com.zrt.ptt.app.console.mvp.model.Imodel.IConsoleMap;

import java.util.List;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by surpass on 2017-5-18.
 */

public class ConsoleMapModel implements IConsoleMap {
    @Override
    public void getUserTraceHistory(CallBackTraceHistory callback,List<String> traceHistoryUserIds,Long startTime, long endTime) {
        ((AppComponent) App.getInstance().getApplicationContext()).getSignalBroker()
                .findUserLocations(traceHistoryUserIds,startTime,endTime)
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<UserLocation>>() {
                    @Override
                    public void onSubscribe(Disposable disposable) {
                        if(disposable!=null){
                            callback.callBackDisposable(disposable);
                        }
                    }

                    @Override
                    public void onNext(List<UserLocation> userLocations) {
                        callback.callBackTraceDatas(userLocations);
                    }

                    @Override
                    public void onError(Throwable throwable) {


                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }
}
