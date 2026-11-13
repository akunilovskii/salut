package com.newtube.mobile.ui.playback;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.Arrays;
import java.util.List;

/** Native Android PiP transport actions in the same order as YouTube. */
@RequiresApi(Build.VERSION_CODES.O)
final class PipControls {
    static final String ACTION_TOGGLE = "com.newtube.mobile.action.PIP_TOGGLE";
    static final String ACTION_PREVIOUS = "com.newtube.mobile.action.PIP_PREVIOUS";
    static final String ACTION_NEXT = "com.newtube.mobile.action.PIP_NEXT";

    private PipControls() {}

    static List<RemoteAction> create(Context context, boolean playing, boolean hasPrevious, boolean hasNext) {
        return Arrays.asList(
                action(context, ACTION_PREVIOUS, 701, R.drawable.ic_pip_previous,
                        R.string.mobile_player_previous, hasPrevious),
                action(context, ACTION_TOGGLE, 700,
                        playing ? R.drawable.ic_player_pause : R.drawable.ic_player_play,
                        playing ? R.string.mobile_player_pause : R.string.mobile_player_play, true),
                action(context, ACTION_NEXT, 702, R.drawable.ic_pip_next,
                        R.string.mobile_player_next, hasNext));
    }

    private static RemoteAction action(Context context, String command, int requestCode,
                                       int iconResource, int labelResource, boolean enabled) {
        PendingIntent intent = PendingIntent.getBroadcast(context, requestCode,
                new Intent(command).setPackage(context.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String label = context.getString(labelResource);
        RemoteAction action = new RemoteAction(Icon.createWithResource(context, iconResource), label, label, intent);
        action.setEnabled(enabled);
        return action;
    }
}
