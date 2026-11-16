package com.newtube.mobile.feedback;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.Button;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Protocol;
import okhttp3.MediaType;
import okio.Timeout;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "ru")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class FeedbackFormTest {
    @Test public void localizedLabelsKeepStablePayloadCategoriesAndResolvedHints() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_NewTube);
        activity.getSharedPreferences("feedback_draft", Activity.MODE_PRIVATE).edit().clear().commit();
        FeedbackForm form = new FeedbackForm();
        try {
            form.show(activity, "Subscriptions");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Field dialogField = FeedbackForm.class.getDeclaredField("dialog");
            dialogField.setAccessible(true);
            AlertDialog dialog = (AlertDialog) dialogField.get(form);
            List<Spinner> spinners = new ArrayList<>();
            List<TextInputLayout> fields = new ArrayList<>();
            collect(dialog.getWindow().getDecorView(), spinners, fields);
            assertEquals(2, spinners.size());
            Spinner screen = spinners.get(1);
            assertEquals("Подписки", screen.getSelectedItem().toString());
            assertEquals(4, fields.size());
            assertEquals(activity.getString(R.string.feedback_description), fields.get(1).getHint().toString());
            screen.setSelection(4);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("Канал", screen.getSelectedItem().toString());
            FeedbackDraft restored = new FeedbackDraft(activity.getSharedPreferences("feedback_draft", Activity.MODE_PRIVATE));
            assertEquals("Channel", restored.screen);
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        } finally {
            form.close();
            activity.finish();
        }
    }

    @Test public void dismissalBeforeScheduledExecutionCancelsWithoutSending() throws Exception {
        try (FormFixture fixture = new FormFixture()) {
            fixture.send.performClick();
            assertFalse(fixture.send.isEnabled());
            fixture.dialog.dismiss();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(fixture.transport.latest.cancelled);
            fixture.scheduler.triggerActions();
            assertEquals(0, fixture.transport.executions);
            fixture.assertDraftPreserved();
        }
    }

    @Test public void closingInFlightCancelsTheExecutingCallAndPreservesDraft() throws Exception {
        try (FormFixture fixture = new FormFixture()) {
            fixture.transport.onExecute = () -> {
                fixture.dialog.dismiss();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertTrue(fixture.transport.latest.cancelled);
            };
            fixture.send.performClick();
            fixture.scheduler.triggerActions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, fixture.transport.executions);
            assertFalse(fixture.dialog.isShowing());
            fixture.assertDraftPreserved();
        }
    }

    @Test public void duplicateSendIsSuppressedAndNetworkFailureAllowsExactRetry() throws Exception {
        try (FormFixture fixture = new FormFixture()) {
            fixture.transport.fail = true;
            fixture.send.performClick();
            fixture.send.performClick();
            assertEquals(1, fixture.transport.created);
            assertFalse(fixture.send.isEnabled());
            fixture.scheduler.triggerActions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, fixture.transport.executions);
            assertTrue(fixture.dialog.isShowing());
            assertTrue(fixture.send.isEnabled());
            fixture.assertDraftPreserved();
            String first = fixture.transport.latest.payload();
            assertEquals(first, fixture.restored().prepare("version changed", "updated"));
            fixture.send.performClick();
            assertEquals(2, fixture.transport.created);
            assertEquals(first, fixture.transport.latest.payload());
            assertEquals(first, fixture.restored().prepare("another update", "newer"));
        }
    }

    private static final class FormFixture implements AutoCloseable {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final TestScheduler scheduler = new TestScheduler();
        final FakeTransport transport = new FakeTransport();
        final FeedbackForm form = new FeedbackForm(transport, scheduler);
        final AlertDialog dialog;
        final Button send;

        FormFixture() throws Exception {
            android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            info.versionName = "1.18.0";
            Shadows.shadowOf(activity.getPackageManager()).installPackage(info);
            activity.setTheme(R.style.Theme_NewTube);
            activity.getSharedPreferences("feedback_draft", Activity.MODE_PRIVATE).edit().clear().commit();
            FeedbackDraft draft = restored();
            draft.kind = "bug"; draft.description = "Keep this report"; draft.steps = "Reproduce"; draft.save();
            form.show(activity, "Home");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Field field = FeedbackForm.class.getDeclaredField("dialog");
            field.setAccessible(true);
            dialog = (AlertDialog) field.get(form);
            send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            assertTrue(send.isEnabled());
        }
        FeedbackDraft restored() {
            return new FeedbackDraft(activity.getSharedPreferences("feedback_draft", Activity.MODE_PRIVATE));
        }
        void assertDraftPreserved() {
            assertEquals("Keep this report", restored().description);
            assertEquals("Reproduce", restored().steps);
        }
        public void close() { form.close(); activity.finish(); }
    }

    /** Deterministic transport: no sockets, sleeps or production endpoint calls. */
    private static final class FakeTransport implements Call.Factory {
        int created, executions;
        boolean fail;
        Runnable onExecute;
        FakeCall latest;
        public Call newCall(Request request) { created++; return latest = new FakeCall(request); }
        private final class FakeCall implements Call {
            final Request request;
            boolean cancelled, executed;
            FakeCall(Request request) { this.request = request; }
            String payload() throws IOException {
                okio.Buffer buffer = new okio.Buffer();
                request.body().writeTo(buffer);
                return buffer.readUtf8();
            }
            public Request request() { return request; }
            public Response execute() throws IOException {
                executions++; executed = true;
                if (onExecute != null) onExecute.run();
                if (fail) throw new IOException("Offline");
                return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(503).message("Unavailable")
                        .body(ResponseBody.create(MediaType.parse("application/json"), "{}")).build();
            }
            public void enqueue(Callback callback) { throw new UnsupportedOperationException(); }
            public void cancel() { cancelled = true; }
            public boolean isExecuted() { return executed; }
            public boolean isCanceled() { return cancelled; }
            public Timeout timeout() { return new Timeout(); }
            public Call clone() { return new FakeCall(request); }
        }
    }

    private static void collect(View view, List<Spinner> spinners, List<TextInputLayout> fields) {
        if (view instanceof Spinner) spinners.add((Spinner) view);
        if (view instanceof TextInputLayout) fields.add((TextInputLayout) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), spinners, fields);
        }
    }
}
