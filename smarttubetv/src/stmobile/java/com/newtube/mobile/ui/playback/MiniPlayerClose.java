package com.newtube.mobile.ui.playback;

import android.os.Handler;
import android.os.Looper;

import com.newtube.mobile.ui.common.Motion;

/** Stops audio immediately and keeps teardown independent of a view animation's end callback. */
final class MiniPlayerClose {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mFinish;
    private Runnable mResume;
    private Runnable mAbort;
    private final Runnable mTimeout = () -> {
        Runnable finish = mFinish;
        complete();
        if (finish != null) finish.run();
    };

    void start(Runnable pause, Runnable resume, Runnable abort, Runnable finish) {
        complete();
        mFinish = finish;
        mResume = resume;
        mAbort = abort;
        pause.run();
        // A cancelled animator does not invoke withEndAction. Allow one short grace interval.
        mHandler.postDelayed(mTimeout, Motion.EXIT_MS + 100);
    }

    void complete() {
        mHandler.removeCallbacks(mTimeout);
        mFinish = null;
        mResume = null;
        mAbort = null;
    }

    void cancel() {
        Runnable resume = mResume;
        Runnable abort = mAbort;
        complete();
        if (abort != null) abort.run();
        if (resume != null) resume.run();
    }
}
