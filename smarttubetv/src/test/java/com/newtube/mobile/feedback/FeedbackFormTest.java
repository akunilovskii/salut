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
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Protocol;
import okhttp3.MediaType;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
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
            int[] labels = {R.string.feedback_function, R.string.feedback_description,
                    R.string.feedback_steps, R.string.feedback_expected};
            int[] limits = {200, 6000, 4000, 4000};
            for (int i = 0; i < fields.size(); i++) {
                TextInputLayout input = fields.get(i);
                assertEquals(activity.getString(labels[i]), input.getHint().toString());
                assertFalse("Limits belong in counters, not hints", input.getHint().toString().matches(".*[0-9].*"));
                assertTrue(input.isCounterEnabled());
                assertEquals(limits[i], input.getCounterMaxLength());
            }
            // Counters display limits without truncating the user's draft or changing validation.
            fields.get(0).getEditText().setText(new String(new char[201]).replace('\0', 'x'));
            assertEquals(201, fields.get(0).getEditText().length());
            fields.get(0).getEditText().setText("");
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
        public Call newCall(Request request) {
            created++;
            latest = new FakeCall(request);
            return latest.call;
        }
        private final class FakeCall {
            final Request request;
            boolean cancelled, executed;
            final Call call;
            FakeCall(Request request) {
                this.request = request;
                // Keep this test double independent of new abstract OkHttp Call methods.
                call = (Call) Proxy.newProxyInstance(Call.class.getClassLoader(), new Class<?>[]{Call.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "request": return request;
                                case "execute": return execute();
                                case "cancel": cancelled = true; return null;
                                case "isExecuted": return executed;
                                case "isCanceled": return cancelled;
                                case "toString": return "FeedbackTestCall";
                                case "hashCode": return System.identityHashCode(proxy);
                                case "equals": return proxy == args[0];
                                default: throw new AssertionError("Unexpected Call method: " + method);
                            }
                        });
            }
            String payload() throws IOException {
                okio.Buffer buffer = new okio.Buffer();
                request.body().writeTo(buffer);
                return buffer.readUtf8();
            }
            public Response execute() throws IOException {
                executions++; executed = true;
                if (onExecute != null) onExecute.run();
                if (fail) throw new IOException("Offline");
                return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(503).message("Unavailable")
                        .body(ResponseBody.create(MediaType.parse("application/json"), "{}")).build();
            }

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
