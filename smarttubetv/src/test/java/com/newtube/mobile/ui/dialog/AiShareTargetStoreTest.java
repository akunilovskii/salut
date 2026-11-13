package com.newtube.mobile.ui.dialog;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.widget.EditText;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowDialog;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AiShareTargetStoreTest {
    private Activity activity;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_NewTube);
        activity.getSharedPreferences("salut_ai_share", 0).edit().clear().commit();
    }

    @Test public void remembersDestinationAcrossStoreInstancesAndRejectsRemovedApps() {
        ResolveInfo app = register("assistant.one", "Assistant");
        new AiShareTargetStore(activity).remember(app);
        assertEquals("assistant.one", new AiShareTargetStore(activity).restore(activity).activityInfo.packageName);
        shadowOf(activity.getPackageManager()).removeResolveInfosForIntent(AiShareTargetStore.sendIntent(""), "assistant.one");
        assertNull(new AiShareTargetStore(activity).restore(activity));
        register("assistant.one", "Assistant");
        assertNull(new AiShareTargetStore(activity).restore(activity));
    }

    @Test public void choosingAnAppDoesNotSendAndPreservesEditedInstruction() {
        register("assistant.one", "Assistant");
        Video video = new Video(); video.videoId = "abc123"; video.title = "Example";
        AlertDialog review = AiVideoShare.review(activity, video, "Original words");
        assertFalse(review.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertNull(review.getButton(AlertDialog.BUTTON_POSITIVE).getBackground());
        EditText instruction = findEditor(review.getWindow().getDecorView());
        assertNotNull(instruction);
        instruction.setText("My edited instruction");
        review.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        AlertDialog picker = (AlertDialog) ShadowDialog.getLatestDialog();
        picker.getListView().performItemClick(picker.getListView().getChildAt(0), 0, 0);
        assertTrue(review.isShowing());
        assertNull(shadowOf(activity).getNextStartedActivity());
        assertEquals("My edited instruction", instruction.getText().toString());
        assertEquals("Assistant", review.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
        assertTrue(review.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertNull(review.getButton(AlertDialog.BUTTON_POSITIVE).getBackground());
        assertEquals(androidx.core.content.ContextCompat.getColor(activity, R.color.mobile_color_on_surface),
                review.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor());
        review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Intent sent = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_SEND, sent.getAction());
        assertEquals("assistant.one", sent.getComponent().getPackageName());
        assertTrue(sent.getStringExtra(Intent.EXTRA_TEXT).startsWith("My edited instruction\n"));
        assertFalse(review.isShowing());
        AlertDialog reopened = AiVideoShare.review(activity, video, "");
        assertTrue(reopened.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertEquals("Assistant", reopened.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());
        reopened.dismiss();
    }

    @Test public void unavailableTargetsAreNeverOffered() {
        ResolveInfo app = register("assistant.hidden", "Hidden");
        app.activityInfo.exported = false;
        assertTrue(AiShareTargetStore.available(activity).isEmpty());
    }

    @Test public void narrowDialogKeepsLongAppNameAndActionsInsideTheWindow() {
        ResolveInfo app = register("assistant.long", "An assistant with a long application name");
        new AiShareTargetStore(activity).remember(app);
        Video video = new Video(); video.videoId = "abc123";
        AlertDialog dialog = AiVideoShare.review(activity, video, "Original words");
        View decor = dialog.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.AT_MOST));
        decor.layout(0, 0, 320, decor.getMeasuredHeight());
        for (int id : new int[] {AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE}) {
            View button = dialog.getButton(id);
            android.graphics.Rect bounds = new android.graphics.Rect(0, 0, button.getWidth(), button.getHeight());
            ((ViewGroup) decor).offsetDescendantRectToMyCoords(button, bounds);
            assertTrue(bounds.toString(), bounds.left >= 0 && bounds.right <= 320);
            assertTrue(bounds.toString(), button.getHeight() >= 48 && button.getWidth() > 0);
        }
        dialog.dismiss();
    }

    private ResolveInfo register(String packageName, String label) {
        ResolveInfo app = new ResolveInfo();
        app.nonLocalizedLabel = "Share action";
        app.activityInfo = new ActivityInfo();
        app.activityInfo.packageName = packageName;
        app.activityInfo.name = packageName + ".Share";
        app.activityInfo.enabled = app.activityInfo.exported = true;
        app.activityInfo.applicationInfo = new ApplicationInfo();
        app.activityInfo.applicationInfo.packageName = packageName;
        app.activityInfo.applicationInfo.nonLocalizedLabel = label;
        app.activityInfo.applicationInfo.enabled = true;
        shadowOf(activity.getPackageManager()).addResolveInfoForIntent(AiShareTargetStore.sendIntent(""), app);
        return app;
    }

    private EditText findEditor(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            EditText found = findEditor(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
