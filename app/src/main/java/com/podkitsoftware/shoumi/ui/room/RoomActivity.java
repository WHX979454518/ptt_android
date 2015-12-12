package com.podkitsoftware.shoumi.ui.room;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.podkitsoftware.shoumi.R;
import com.podkitsoftware.shoumi.ui.base.BaseActivity;

/**
 * 房间对话界面
 *
 * Created by fanchao on 11/12/15.
 */
public class RoomActivity extends BaseActivity implements RoomFragment.Callbacks {

    public static final String EXTRA_ROOM_ID = "extra_room_id";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_room);
        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);

        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.room_content, RoomFragment.create(intent.getStringExtra(EXTRA_ROOM_ID)))
                .commit();
    }

    public static Intent builder(final Context context, final String roomId) {
        return new Intent(context, RoomActivity.class)
                .putExtra(EXTRA_ROOM_ID, roomId);
    }
}
