package com.newtube.mobile.ui.playback;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.Rational;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.media3.ui.AspectRatioFrameLayout;

import java.lang.ref.WeakReference;

/** Renders a mini host's live session in PiP without starting an Activity from the background. */
public final class MiniHostPip {
    private static WeakReference<MiniHostPip> sActive = new WeakReference<>(null);
    private static boolean sKeepPlayerDocked;
    private final Activity mActivity;
    private FrameLayout mOverlay;
    private final PipDismissal mDismissal = new PipDismissal();

    public MiniHostPip(Activity activity) {
        mActivity = activity;
    }

    public boolean isActive() {
        return mOverlay != null;
    }

    public static boolean isAnyActive() {
        MiniHostPip hostPip = sActive.get();
        return hostPip != null && hostPip.isActive();
    }

    static boolean shouldKeepPlayerDocked() {
        return sKeepPlayerDocked;
    }

    static void clearDockedReturn() {
        sKeepPlayerDocked = false;
    }

    static void refreshActions() {
        MiniHostPip hostPip = sActive.get();
        if (hostPip == null || !hostPip.isActive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        try {
            hostPip.mActivity.setPictureInPictureParams(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .setActions(MiniPlayerBridge.getPipActions()).build());
        } catch (IllegalStateException ignored) {
            // A host being dismissed is no longer able to update its PiP controls.
        }
    }

    public boolean enter(MiniPlayerBridge.MiniHost host) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isActive()
                || !MiniPlayerBridge.canEnterSystemPip()) return false;

        ViewGroup content = mActivity.findViewById(android.R.id.content);
        if (content == null) return false;
        Rect source = host.getMiniCardBoundsOnScreen();
        host.detachMiniVideoForPip();

        FrameLayout overlay = new FrameLayout(mActivity);
        overlay.setBackgroundColor(Color.BLACK);
        AspectRatioFrameLayout videoFrame = new AspectRatioFrameLayout(mActivity);
        videoFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        overlay.addView(videoFrame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        MiniPlayerBridge.fitToVideo(videoFrame);

        Bitmap still = MiniPlayerBridge.takeHandoffStill();
        ImageView freeze = new ImageView(mActivity);
        freeze.setScaleType(ImageView.ScaleType.FIT_CENTER);
        freeze.setImageBitmap(still);
        videoFrame.addView(freeze, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextureView texture = new TextureView(mActivity);
        texture.setOpaque(false);
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture created, int width, int height) {
                SurfaceTexture session = MiniPlayerBridge.getSessionTexture();
                if (session != null && created != session) {
                    texture.setSurfaceTexture(session);
                    created.release();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return surface != MiniPlayerBridge.getSessionTexture();
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                freeze.setVisibility(View.GONE);
                freeze.setImageDrawable(null);
            }
        });
        videoFrame.addView(texture, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mOverlay = overlay;
        sActive = new WeakReference<>(this);

        PictureInPictureParams.Builder params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .setActions(MiniPlayerBridge.getPipActions());
        if (source != null && !source.isEmpty()) params.setSourceRectHint(source);
        boolean entered;
        try {
            entered = mActivity.enterPictureInPictureMode(params.build());
        } catch (IllegalStateException error) {
            entered = false;
        }
        if (entered) {
            sKeepPlayerDocked = true;
            mDismissal.onEntered();
        } else {
            close();
        }
        return entered;
    }

    public void onHostResumed() {
        mDismissal.onResumed();
        close();
    }

    public void onHostDestroyed(boolean changingConfigurations) {
        // Older PiP shells destroy the host on dismissal without an exit-mode callback.
        if (isActive() && !changingConfigurations) MiniPlayerBridge.close();
        close();
    }

    public void onHostStopped() {
        if (mDismissal.onStopped()) MiniPlayerBridge.close();
    }

    public void onPipModeChanged(boolean inPip) {
        if (inPip) {
            mDismissal.onEntered();
        } else {
            close();
            if (mDismissal.onExited()) MiniPlayerBridge.close();
        }
    }

    public void close() {
        FrameLayout overlay = mOverlay;
        mOverlay = null;
        if (sActive.get() == this) sActive = new WeakReference<>(null);
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
    }
}
