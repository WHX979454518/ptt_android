package com.zrt.ptt.app.console.mvp.view.IView;

import com.baidu.mapapi.model.LatLng;
import com.zrt.ptt.app.console.mvp.model.Node;

import java.util.List;

/**
 * Created by surpass on 2017-5-9.
 */

public interface IConsoMapView {
    void showUsersLocation(List<LatLng> locations);
    void showHistoryDialog();

    int getLayoutVisibility();

    void sendCheckedUsers(List<Node> checkedNodes);
}
