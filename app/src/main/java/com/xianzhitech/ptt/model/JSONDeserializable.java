package com.xianzhitech.ptt.model;

import org.json.JSONObject;

/**
 * Created by fanchao on 15/12/15.
 */
public interface JSONDeserializable {
    JSONDeserializable readFrom(JSONObject object);
}
