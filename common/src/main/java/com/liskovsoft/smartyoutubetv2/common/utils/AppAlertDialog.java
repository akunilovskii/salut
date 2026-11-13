package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.liskovsoft.smartyoutubetv2.common.R;
import java.util.function.Function;

/** Allows the phone shell to theme shared edit dialogs while keeping the TV builder unchanged. */
public final class AppAlertDialog {
    private static Function<Context, AlertDialog.Builder> factory;
    private AppAlertDialog() { }

    public static void setFactory(Function<Context, AlertDialog.Builder> value) { factory = value; }

    public static AlertDialog.Builder builder(Context context) {
        return factory != null ? factory.apply(context) : new AlertDialog.Builder(context, R.style.AppDialog);
    }
}
