package com.zrt.ptt.app.console.mvp.view.IView;

import com.baidu.mapapi.model.LatLng;
import com.xianzhitech.ptt.api.dto.LastLocationByUser;
import com.xianzhitech.ptt.broker.RoomMode;
import com.zrt.ptt.app.console.mvp.model.Imodel.IOranizationMain;
import com.zrt.ptt.app.console.mvp.model.Node;

import org.json.JSONObject;

import java.util.List;

/**
 * Created by surpass on 2017-4-27.
 */

public interface IMainActivityView {

//    public enum EChatType{
//        ChatTypeText, //文字聊天
//        ChatTypeAudio, //语音聊天
//        ChatTypeVideo, //视频聊天
//    }

    void UpDateOrganization(JSONObject json);

    /**
     * 点击定位传递数据接口方法
     * @param lastLocationByUsers
     */
    void showLocations(List<LastLocationByUser> lastLocationByUsers);

    /**
     * 创建房间并加入房间聊天，跳转到聊天界面
     * @param userIds 参与聊天的用户
     * @param groupIds 参与聊天的预定义组
     * @param roomMode 聊天类型
     */
    void showChatRoomView(List<String> userIds, List<String> groupIds, RoomMode roomMode);
    void showHistorytraceDialog();

    int getLayoutVisible();

    void sendCheckedUsers(List<Node> checkedNodes);
    void setCompanyName(String compayName);
}
