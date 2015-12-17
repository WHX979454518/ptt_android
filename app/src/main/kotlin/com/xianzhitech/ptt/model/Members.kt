package com.xianzhitech.ptt.model

import android.support.v4.util.ArrayMap
import com.xianzhitech.ptt.ext.toStringList
import org.json.JSONArray

/**
 * Created by fanchao on 17/12/15.
 */

class GroupMembers {
    companion object {
        public const val TABLE_NAME = "group_members"

        public const val COL_GROUP_ID = "gm_group_id"
        public const val COL_PERSON_ID = "gm_person_id"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME(" +
                "$COL_GROUP_ID TEXT NOT NULL REFERENCES ${Group.TABLE_NAME}(${Group.COL_ID}) ON DELETE CASCADE," +
                "$COL_PERSON_ID TEXT NOT NULL REFERENCES ${Person.TABLE_NAME}(${Person.COL_ID}) ON DELETE CASCADE, " +
                "UNIQUE ($COL_GROUP_ID,$COL_PERSON_ID) ON CONFLICT REPLACE" +
                ")"
    }
}

class ConversationMembers {
    companion object {
        public const val TABLE_NAME = "conversation_members"

        public const val COL_CONVERSATION_ID = "cm_conv_id"
        public const val COL_PERSON_ID = "cm_person_id"

        public const val CREATE_TABLE_SQL = "CREATE TABLE $TABLE_NAME (" +
                "$COL_CONVERSATION_ID TEXT NOT NULL REFERENCES ${Conversation.TABLE_NAME}(${Conversation.COL_ID})," +
                "$COL_PERSON_ID TEXT NOT NULL REFERENCES ${Person.TABLE_NAME}(${Person.COL_ID})" +
                ")"
    }
}

public fun JSONArray?.toGroupsAndMembers(): Map<String, Iterable<String>> {
    if (this == null) {
        return emptyMap()
    }

    val size = length()
    val result = ArrayMap<String, Iterable<String>>(size)
    for (i in 1..size - 1) {
        val groupObject = getJSONObject(i)
        result.put(groupObject.getString("idNumber"), groupObject.optJSONArray("members").toStringList())
    }

    return result
}

public fun JSONArray?.toConversationsAndMembers() = toGroupsAndMembers()