package com.newtube.mobile.ui.playback;

import android.widget.ImageButton;

import androidx.core.view.ViewCompat;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Binds the player shortcut to the existing global last-speed memory and speed picker. */
final class PlayerSpeedButton {
    private PlayerSpeedButton() {
    }

    static void bind(ImageButton button, PlayerData data, Supplier<Float> currentSpeed,
            Consumer<Float> applySpeed, Runnable showPicker) {
        button.setOnClickListener(view -> {
            float current = currentSpeed.get();
            float remembered = data.getLastSpeed();
            if (!Helpers.floatEquals(current, 1f)) {
                applySpeed.accept(1f);
            } else if (remembered > 0 && !Helpers.floatEquals(remembered, 1f)) {
                applySpeed.accept(remembered);
            } else {
                showPicker.run();
            }
        });
        button.setOnLongClickListener(view -> {
            showPicker.run();
            return true;
        });
        refresh(button, currentSpeed.get());
    }

    static void refresh(ImageButton button, float speed) {
        if (button == null) {
            return;
        }
        boolean changed = speed > 0 && !Helpers.floatEquals(speed, 1f);
        button.setSelected(changed);
        String value = changed ? speed + "x" : button.getContext().getString(R.string.mobile_speed_normal);
        ViewCompat.setStateDescription(button, value);
    }
}
