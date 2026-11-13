package com.newtube.mobile.casting;

import android.app.Activity;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.Locale;

/**
 * The remote-volume indicator: a floating top-center pill with a draggable slider and percent
 * readout. One instance lazily attaches to an activity's content view and is reused;
 * it auto-hides {@value #AUTO_HIDE_MS}ms after the last key press or drag.
 *
 * <p>Dragging sets absolute volume on the selected output. Changes are throttled because Cast
 * can send an HTTP request per step. The final value always lands. Programmatic
 * {@code setProgress} echoes into the listener with {@code fromUser=false} and is ignored - only
 * real drags talk to the output.</p>
 *
 * <p>Main-thread confined (key dispatch + touch), like the session manager it drives.</p>
 */
public final class RemoteVolumeOverlay {
    public interface VolumeSetter {
        void setVolumePercent(int volumePercent);
    }

    private static final long AUTO_HIDE_MS = 4_000;
    private static final long FADE_MS = 150;
    private static final long SEND_THROTTLE_MS = 200;

    private final View mRoot;
    private final SeekBar mSlider;
    private final TextView mValue;
    private final ImageView mIcon;
    private final Runnable mHide = this::hideNow;
    private final Runnable mSendPending = () -> sendPending(true);
    /** Finger currently on the slider: suppress auto-hide until it lifts. */
    private boolean mTracking;
    private long mLastSendUptime;
    /** Latest dragged value not yet sent to the TV; -1 when nothing is pending. */
    private int mPendingPercent = -1;
    private VolumeSetter mSetter;

    /** Show (or refresh) the overlay in this activity at the given volume. */
    public static void show(Activity activity, int volumePercent, VolumeSetter setter,
                            boolean switchOutput) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        View existing = content.findViewById(R.id.mobile_cast_volume_overlay);
        RemoteVolumeOverlay overlay = existing != null
                ? (RemoteVolumeOverlay) existing.getTag()
                : new RemoteVolumeOverlay(activity, content);
        overlay.mSetter = setter;
        overlay.mIcon.setImageResource(switchOutput ? R.drawable.ic_switch_audio_active
                : R.drawable.ic_mobile_cast);
        overlay.mIcon.setContentDescription(activity.getString(switchOutput
                ? R.string.switch_audio_receiver : R.string.mobile_cast_tv_volume_label));
        overlay.placeBelowToolbar(activity, content);
        overlay.showVolume(volumePercent);
    }

    private RemoteVolumeOverlay(Activity activity, ViewGroup content) {
        mRoot = LayoutInflater.from(activity)
                .inflate(R.layout.overlay_mobile_cast_volume, content, false);
        mRoot.setTag(this);
        content.addView(mRoot);
        mSlider = mRoot.findViewById(R.id.cast_volume_slider);
        mValue = mRoot.findViewById(R.id.cast_volume_value);
        mIcon = mRoot.findViewById(R.id.remote_volume_icon);

        mSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mValue.setText(formatPercent(progress));
                if (fromUser) {
                    mPendingPercent = progress;
                    sendPending(false);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mTracking = true;
                mRoot.removeCallbacks(mHide);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mTracking = false;
                sendPending(true); // the value the finger settled on always lands
                scheduleHide();
            }
        });
    }

    private void placeBelowToolbar(Activity activity, ViewGroup content) {
        View toolbar = activity.findViewById(R.id.mobile_top_bar_frame);
        if (toolbar == null) {
            toolbar = activity.findViewById(R.id.mobile_player_options);
        }
        if (toolbar == null) {
            return;
        }
        int[] toolbarLocation = new int[2];
        int[] contentLocation = new int[2];
        toolbar.getLocationOnScreen(toolbarLocation);
        content.getLocationOnScreen(contentLocation);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mRoot.getLayoutParams();
        params.topMargin = Math.max(0, toolbarLocation[1] + toolbar.getHeight()
                - contentLocation[1] + 8);
        mRoot.setLayoutParams(params);
    }

    private void showVolume(int volumePercent) {
        int clamped = Math.max(0, Math.min(100, volumePercent));
        mSlider.setProgress(clamped);
        mValue.setText(formatPercent(clamped)); // setProgress skips its listener when unchanged
        mRoot.animate().cancel();
        mRoot.setAlpha(1f);
        mRoot.setVisibility(View.VISIBLE);
        scheduleHide();
    }

    /** Leading and trailing throttle around the selected remote output. */
    private void sendPending(boolean force) {
        if (mPendingPercent < 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long sinceLast = now - mLastSendUptime;
        if (!force && sinceLast < SEND_THROTTLE_MS) {
            mRoot.removeCallbacks(mSendPending);
            mRoot.postDelayed(mSendPending, SEND_THROTTLE_MS - sinceLast);
            return;
        }
        mLastSendUptime = now;
        int percent = mPendingPercent;
        mPendingPercent = -1;
        mRoot.removeCallbacks(mSendPending);
        if (mSetter != null) mSetter.setVolumePercent(percent);
    }

    private void scheduleHide() {
        mRoot.removeCallbacks(mHide);
        mRoot.postDelayed(mHide, AUTO_HIDE_MS);
    }

    private void hideNow() {
        if (mTracking) {
            return; // finger still down; onStopTrackingTouch reschedules
        }
        mRoot.animate().alpha(0f).setDuration(FADE_MS)
                .withEndAction(() -> mRoot.setVisibility(View.GONE)).start();
    }

    private static String formatPercent(int percent) {
        return String.format(Locale.US, "%d%%", percent);
    }
}
