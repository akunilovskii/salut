package com.newtube.mobile.feedback;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Protocol;
import okhttp3.MediaType;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
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
        FeedbackDraft.Session.forTask(activity.getTaskId()).clear();
        FeedbackForm form = new FeedbackForm();
        try {
            form.show(activity);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Field dialogField = FeedbackForm.class.getDeclaredField("dialog");
            dialogField.setAccessible(true);
            BottomSheetDialog dialog = (BottomSheetDialog) dialogField.get(form);
            List<MaterialAutoCompleteTextView> selectors = new ArrayList<>();
            List<TextInputLayout> fields = new ArrayList<>();
            collect(dialog.getWindow().getDecorView(), selectors, fields);
            assertEquals(1, selectors.size());
            MaterialAutoCompleteTextView screen = selectors.get(0);
            assertEquals("Не указан", screen.getText().toString());
            RadioGroup type = dialog.findViewById(R.id.feedback_type_group);
            assertNotNull(type);
            assertEquals(RadioGroup.HORIZONTAL, type.getOrientation());
            assertEquals(-1, type.getCheckedRadioButtonId());
            assertEquals(4, fields.size());
            assertEquals(activity.getString(R.string.feedback_screen), fields.get(0).getHint().toString());
            int rowGap = ((android.widget.LinearLayout.LayoutParams) fields.get(0).getLayoutParams()).topMargin;
            for (TextInputLayout input : fields) {
                assertFalse(input.isExpandedHintEnabled());
                assertEquals(rowGap, ((android.widget.LinearLayout.LayoutParams) input.getLayoutParams()).topMargin);
            }
            for (int i = 0; i < 1; i++) {
                assertEquals(TextInputLayout.END_ICON_DROPDOWN_MENU, fields.get(i).getEndIconMode());
                assertFalse(fields.get(i).isExpandedHintEnabled());
                assertEquals(android.text.InputType.TYPE_NULL, selectors.get(i).getInputType());
            }
            int[] labels = {R.string.feedback_description, R.string.feedback_steps, R.string.feedback_expected};
            int[] limits = {6000, 4000, 4000};
            for (int i = 0; i < labels.length; i++) {
                TextInputLayout input = fields.get(i + 1);
                assertEquals(activity.getString(labels[i]), input.getHint().toString());
                assertFalse("Limits belong in counters, not hints", input.getHint().toString().matches(".*[0-9].*"));
                assertTrue(input.isCounterEnabled());
                assertEquals(limits[i], input.getCounterMaxLength());
            }
            // Counters display limits without truncating the user's draft or changing validation.
            fields.get(1).getEditText().setText(new String(new char[6001]).replace('\0', 'x'));
            assertEquals(6001, fields.get(1).getEditText().length());
            fields.get(1).getEditText().setText("Report");
            assertFalse(((Button) dialog.findViewById(R.id.feedback_send)).isEnabled());
            type.check(R.id.feedback_type_error);
            assertTrue(((Button) dialog.findViewById(R.id.feedback_send)).isEnabled());
            FeedbackDraft unscoped = new FeedbackDraft(FeedbackDraft.Session.forTask(activity.getTaskId()));
            assertEquals("", new org.json.JSONObject(unscoped.prepare("1", "17")).getString("screen"));
            type.check(R.id.feedback_type_improvement);
            assertFalse(((RadioButton) type.findViewById(R.id.feedback_type_error)).isChecked());
            assertTrue(((RadioButton) type.findViewById(R.id.feedback_type_improvement)).isChecked());
            assertEquals(View.GONE, fields.get(2).getVisibility());
            fields.get(1).getEditText().setText("");
            screen.setText(screen.getAdapter().getItem(5).toString(), false);
            screen.getOnItemClickListener().onItemClick(null, null, 5, 5);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("Канал", screen.getText().toString());
            FeedbackDraft restored = new FeedbackDraft(FeedbackDraft.Session.forTask(activity.getTaskId()));
            assertEquals("Channel", restored.screen);
            assertEquals("enhancement", restored.kind);
            assertFalse(((Button) dialog.findViewById(R.id.feedback_send)).isEnabled());
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

    @Test public void reopeningRetainsSessionDraftAndRetiresDiskCopy() throws Exception {
        try (FormFixture fixture = new FormFixture()) {
            fixture.activity.getSharedPreferences("feedback_draft", Activity.MODE_PRIVATE).edit()
                    .putString("draft", "legacy disk report").commit();
            fixture.form.close();
            FeedbackForm reopened = new FeedbackForm(fixture.transport, fixture.scheduler);
            try {
                reopened.show(fixture.activity);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                Field field = FeedbackForm.class.getDeclaredField("dialog");
                field.setAccessible(true);
                BottomSheetDialog sheet = (BottomSheetDialog) field.get(reopened);
                List<MaterialAutoCompleteTextView> selectors = new ArrayList<>();
                List<TextInputLayout> fields = new ArrayList<>();
                collect(sheet.getWindow().getDecorView(), selectors, fields);
                assertEquals("Keep this report", fields.get(1).getEditText().getText().toString());
                assertEquals("Reproduce", fields.get(2).getEditText().getText().toString());
                assertTrue(sheet.getBehavior().isDraggable());
                assertFalse(fixture.activity.getSharedPreferences("feedback_draft", Activity.MODE_PRIVATE)
                        .contains("draft"));
                assertEquals(0, fixture.transport.created);
            } finally { reopened.close(); }
        }
    }

    private static final class FormFixture implements AutoCloseable {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final TestScheduler scheduler = new TestScheduler();
        final FakeTransport transport = new FakeTransport();
        final FeedbackForm form = new FeedbackForm(transport, scheduler);
        final BottomSheetDialog dialog;
        final Button send;

        FormFixture() throws Exception {
            android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            info.versionName = "1.18.0";
            Shadows.shadowOf(activity.getPackageManager()).installPackage(info);
            activity.setTheme(R.style.Theme_NewTube);
            FeedbackDraft.Session.forTask(activity.getTaskId()).clear();
            FeedbackDraft draft = restored();
            draft.kind = "bug"; draft.description = "Keep this report"; draft.steps = "Reproduce"; draft.save();
            form.show(activity);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Field field = FeedbackForm.class.getDeclaredField("dialog");
            field.setAccessible(true);
            dialog = (BottomSheetDialog) field.get(form);
            send = ((Button) dialog.findViewById(R.id.feedback_send));
            assertTrue(send.isEnabled());
        }
        FeedbackDraft restored() {
            return new FeedbackDraft(FeedbackDraft.Session.forTask(activity.getTaskId()));
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

    private static void collect(View view, List<MaterialAutoCompleteTextView> selectors, List<TextInputLayout> fields) {
        if (view instanceof MaterialAutoCompleteTextView) selectors.add((MaterialAutoCompleteTextView) view);
        if (view instanceof TextInputLayout) fields.add((TextInputLayout) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), selectors, fields);
        }
    }
}
