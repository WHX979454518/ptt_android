package com.xianzhitech.ptt.util;

import com.xianzhitech.ptt.model.Privilege;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by fanchao on 15/12/15.
 */
public class PrivilegeUtil {

    public static @Privilege int fromJson(final JSONObject object){
        @Privilege int result = 0;

        if (hasPrivilege(object, "call")) {
            result |= Privilege.MAKE_CALL;
        }

        if (hasPrivilege(object, "group")) {
            result |= Privilege.CREATE_GROUP;
        }

        if (hasPrivilege(object, "recvCall")) {
            result |= Privilege.RECEIVE_CALL;
        }

        if (hasPrivilege(object, "recvGroup")) {
            result |= Privilege.RECEIVE_GROUP;
        }

        return result;
    }

    private static boolean hasPrivilege(final JSONObject object, final String name) {
        try {
            return object.has(name) && object.getBoolean(name);
        } catch (JSONException e) {
            return false;
        }
    }
}
