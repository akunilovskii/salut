package com.newtube.mobile.audio;

import com.newtube.mobile.ui.common.MobileSnackbar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.newtube.mobile.casting.RemoteVolumeOverlay;
import com.newtube.mobile.ui.playback.MobilePlaybackService;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** One-tap audio route shared by the home toolbar and video controls. */
public final class SwitchAudioButton {
    private static final int REQUEST_NETWORK = 113;
    private static final String LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";
    private static final int VOLUME_KEY_STEP_PERCENT = 5;

    private SwitchAudioButton() { }

    public static void bindVolumeController(Activity activity) {
        MobilePlaybackService.bindActivityVolume(activity);
    }

    public static void clearVolumeController(Activity activity) {
        MobilePlaybackService.clearActivityVolume(activity);
    }

    /** Route physical keys to Switch and display the shared remote-volume slider. */
    public static boolean onDispatchVolumeKey(Activity activity, KeyEvent event) {
        if (event == null || !SwitchAudioOutput.isSelected(activity)) return false;
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            SwitchAudioOutput output = SwitchAudioOutput.get(activity);
            int direction = keyCode == KeyEvent.KEYCODE_VOLUME_UP ? 1 : -1;
            output.setVolume(SwitchAudioOutput.getSavedVolume(activity)
                    + direction * VOLUME_KEY_STEP_PERCENT);
            RemoteVolumeOverlay.show(activity, SwitchAudioOutput.getSavedVolume(activity),
                    output::setVolume, true);
        }
        return true;
    }

    public static void bind(Activity activity, ImageButton button, int normalColor) {
        SharedPreferences preferences = activity.getSharedPreferences("switch_audio_output", Context.MODE_PRIVATE);
        Runnable update = () -> {
            button.setVisibility(SwitchAudioOutput.isButtonShown(activity) ? View.VISIBLE : View.GONE);
            boolean selected = SwitchAudioOutput.isSelected(activity);
            button.setImageTintList(null);
            button.setImageResource(selected ? R.drawable.ic_switch_audio_active : R.drawable.ic_switch_audio);
            if (selected) button.clearColorFilter();
            else button.setColorFilter(ContextCompat.getColor(activity, normalColor));
            button.setContentDescription(activity.getString(R.string.switch_audio_output) + ": "
                    + activity.getString(selected ? R.string.switch_audio_receiver : R.string.switch_audio_phone)
                    + ". " + activity.getString(R.string.switch_audio_adjust_volume));
            button.setSelected(selected);
            if (activity instanceof LifecycleOwner && ((LifecycleOwner) activity).getLifecycle()
                    .getCurrentState().isAtLeast(Lifecycle.State.STARTED)) bindVolumeController(activity);
        };
        SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
            if ("selected".equals(key) || SwitchAudioOutput.SHOW_BUTTON.equals(key)) button.post(update);
        };
        button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                preferences.registerOnSharedPreferenceChangeListener(listener);
                update.run();
            }
            @Override public void onViewDetachedFromWindow(View view) {
                preferences.unregisterOnSharedPreferenceChangeListener(listener);
            }
        });
        if (button.isAttachedToWindow()) preferences.registerOnSharedPreferenceChangeListener(listener);
        update.run();
        button.setOnClickListener(view -> {
            if (!SwitchAudioOutput.isButtonShown(activity)) return;
            SwitchAudioOutput output = SwitchAudioOutput.get(activity);
            if (output.isSelected()) {
                output.select(false);
                MobileSnackbar.show(activity, activity.getString(R.string.switch_audio_local), null, null, 2000);
            } else if (Build.VERSION.SDK_INT >= 36
                    && activity.checkSelfPermission(LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{LOCAL_NETWORK}, REQUEST_NETWORK);
            } else {
                output.select(true);
                MobileSnackbar.show(activity, activity.getString(R.string.switch_audio_connecting), null, null, 2000);
            }
        });
        button.setOnLongClickListener(view -> {
            if (!SwitchAudioOutput.isButtonShown(activity)) return false;
            SwitchAudioOutput output = SwitchAudioOutput.get(activity);
            RemoteVolumeOverlay.show(activity, SwitchAudioOutput.getSavedVolume(activity),
                    output::setVolume, true);
            return true;
        });
    }

    public static boolean handlePermissionResult(Activity activity, int requestCode, int[] results) {
        if (requestCode != REQUEST_NETWORK) return false;
        if (!SwitchAudioOutput.isButtonShown(activity)) return true;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            SwitchAudioOutput.get(activity).select(true);
            MobileSnackbar.show(activity, activity.getString(R.string.switch_audio_connecting), null, null, 2000);
        } else {
            MobileSnackbar.show(activity, activity.getString(R.string.switch_audio_permission), null, null, 3500);
        }
        return true;
    }

}
