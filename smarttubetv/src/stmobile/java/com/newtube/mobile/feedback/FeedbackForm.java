package com.newtube.mobile.feedback;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import okhttp3.*;
import java.util.concurrent.TimeUnit;

/** Lifecycle-bound form; ordinary text inputs retain the keyboard's own dictation support. */
public final class FeedbackForm {
    private final Call.Factory callFactory;
    private final io.reactivex.rxjava3.core.Scheduler networkScheduler;

    public FeedbackForm() { this(null, Schedulers.io()); }

    FeedbackForm(Call.Factory callFactory, io.reactivex.rxjava3.core.Scheduler networkScheduler) {
        this.callFactory = callFactory;
        this.networkScheduler = networkScheduler;
    }

    private BottomSheetDialog dialog;
    private Disposable request;
    private Call call;
    private boolean busy;
    private boolean completed;

    public void close() {
        if (call != null) call.cancel();
        if (request != null) request.dispose();
        if (dialog != null) dialog.dismiss();
    }

    public void show(Activity activity) {
        if (dialog != null && dialog.isShowing()) return;
        completed = false;
        // Retire disk-backed drafts without importing them into this app session.
        activity.getSharedPreferences("feedback_draft", Context.MODE_PRIVATE).edit().remove("draft").apply();
        FeedbackDraft draft = new FeedbackDraft(FeedbackDraft.Session.forTask(activity.getTaskId()));
        Context context = new androidx.appcompat.view.ContextThemeWrapper(activity, R.style.MobileAlertDialog);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int inset = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(inset, 0, inset, 0);
        String app;
        try { app = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
        catch (Exception e) { return; }
        final String appVersion = app;
        TextView note = new TextView(context);
        note.setText(R.string.feedback_note);
        content.addView(note);
        RadioGroup type = typeSelector(content, draft.kind);
        String[] categories = {"", "Home", "Subscriptions", "Watch later", "Playlist", "Channel", "Search", "Player", "Settings", "Other"};
        MaterialAutoCompleteTextView screen = dropdown(content, R.string.feedback_screen,
                R.array.feedback_screens, categories, draft.screen);
        EditText description = field(content, R.string.feedback_description, draft.description, true, 6000);
        description.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        // Double the normal three-line field including its themed inner padding.
        int descriptionHeight = description.getLineHeight() * description.getMinLines()
                + description.getCompoundPaddingTop() + description.getCompoundPaddingBottom();
        description.setMinHeight(descriptionHeight * 2);
        EditText steps = field(content, R.string.feedback_steps, draft.steps, true, 4000);
        EditText expected = field(content, R.string.feedback_expected, draft.expected, true, 4000);
        TextView versions = new TextView(context);
        versions.setText(activity.getString(R.string.feedback_versions, app, Build.VERSION.RELEASE));
        versions.setTextAppearance(context, R.style.MobileAlertDialogBody);
        addFormRow(content, versions);
        View surface = LayoutInflater.from(context).inflate(R.layout.sheet_mobile_feedback, null);
        androidx.core.widget.NestedScrollView scroll = surface.findViewById(R.id.feedback_scroll);
        content.setPadding(inset, 0, inset, Math.round(16 * activity.getResources().getDisplayMetrics().density));
        scroll.addView(content);
        dialog = new BottomSheetDialog(activity);
        final BottomSheetDialog sheet = dialog;
        dialog.setContentView(surface);
        dialog.getBehavior().setMaxWidth(-1);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        View decor = dialog.getWindow().getDecorView();
        Runnable fit = () -> {
            int height = decor.getHeight();
            if (height <= 0) height = activity.getWindow().getDecorView().getHeight();
            if (height <= 0) return;
            int cap = Math.round(height * 0.85f);
            if (sheet.getBehavior().getMaxHeight() != cap) {
                sheet.getBehavior().setMaxHeight(cap);
                surface.requestLayout();
            }
        };
        decor.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit.run());
        surface.post(fit);
        Button send = surface.findViewById(R.id.feedback_send);
        Button cancel = surface.findViewById(R.id.feedback_cancel);
        cancel.setBackground(null);
        cancel.setOnClickListener(v -> sheet.dismiss());
        send.setBackground(null);
        send.setTextColor(androidx.core.content.ContextCompat.getColorStateList(context, R.color.mobile_dialog_button_text));
        Runnable update = () -> {
            draft.description = description.getText().toString();
            draft.steps = steps.getText().toString(); draft.expected = expected.getText().toString();
            ((View) steps.getParent().getParent()).setVisibility("bug".equals(draft.kind) ? View.VISIBLE : View.GONE);
            ((View) expected.getParent().getParent()).setVisibility("bug".equals(draft.kind) ? View.VISIBLE : View.GONE);
            if (!completed) draft.save();
            send.setEnabled(!busy && draft.canSend());
        };
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            public void afterTextChanged(Editable s) { }
        };
        for (EditText edit : new EditText[]{description, steps, expected}) edit.addTextChangedListener(watcher);
        type.setOnCheckedChangeListener((group, checkedId) -> {
            draft.kind = checkedId == R.id.feedback_type_error ? "bug"
                    : checkedId == R.id.feedback_type_improvement ? "enhancement" : "";
            update.run();
        });
        screen.setOnItemClickListener((parent, view, position, id) -> {
            draft.screen = categories[position]; update.run();
        });
        update.run();
        dialog.setOnDismissListener(d -> {
            if (!completed) draft.save();
            if (call != null) call.cancel();
            if (request != null) request.dispose();
            busy = false;
        });
        send.setOnClickListener(v -> {
            if (busy) return;
            final String payload;
            try { payload = draft.prepare(appVersion, Build.VERSION.RELEASE); }
            catch (Exception e) { MobileSnackbar.show(activity, R.string.feedback_invalid); return; }
            busy = true;
            for (EditText edit : new EditText[]{description, steps, expected}) edit.setEnabled(false);
            setTypeEnabled(type, false); setSelectorEnabled(screen, false); update.run();
            // Publish the request-local call before scheduling so dismissal can always cancel it.
            final Call requestCall = createCall(payload);
            call = requestCall;
            request = Observable.fromCallable(() -> {
                try (Response response = requestCall.execute()) {
                    String receipt = response.body() == null ? "" : response.body().string();
                    if (FeedbackDraft.accepted(response.code(), receipt, payload)) return 0;
                    return response.code() == 429 ? R.string.feedback_rate : response.code() == 400 || response.code() == 413
                            || response.code() == 409 ? R.string.feedback_invalid : R.string.feedback_failed;
                }
            }).doFinally(requestCall::cancel).subscribeOn(networkScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
                if (activity.isDestroyed() || activity.isFinishing() || !dialog.isShowing()) return;
                if (result == 0) {
                    completed = true; draft.clear(); dialog.dismiss();
                    MobileSnackbar.show(activity, R.string.feedback_success);
                } else { restore(type, screen, description, steps, expected); update.run();
                    MobileSnackbar.show(activity, result); }
            }, error -> {
                if (activity.isDestroyed() || activity.isFinishing() || !dialog.isShowing()) return;
                restore(type, screen, description, steps, expected); update.run();
                MobileSnackbar.show(activity, R.string.feedback_failed);
            });
        });
    }

    private Call createCall(String payload) {
        Request request = new Request.Builder().url("https://150.136.243.44/v1/feedback")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload)).build();
        if (callFactory != null) return callFactory.newCall(request);
        // Share scheduling/pooling, but never inherit legacy TLS overrides or interceptors.
        OkHttpClient shared = OkHttpManager.instance().getClient();
        return new OkHttpClient.Builder().dispatcher(shared.dispatcher()).connectionPool(shared.connectionPool())
                .followRedirects(false).followSslRedirects(false)
                .callTimeout(25, TimeUnit.SECONDS).build().newCall(request);
    }

    private void restore(RadioGroup type, MaterialAutoCompleteTextView screen, EditText... fields) {
        busy = false; setTypeEnabled(type, true); setSelectorEnabled(screen, true);
        for (EditText field : fields) field.setEnabled(true);
    }

    private static RadioGroup typeSelector(LinearLayout parent, String selected) {
        View container = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_feedback_type, parent, false);
        RadioGroup group = container.findViewById(R.id.feedback_type_group);
        String[] labels = parent.getResources().getStringArray(R.array.feedback_types);
        ((RadioButton) group.findViewById(R.id.feedback_type_error)).setText(labels[0]);
        ((RadioButton) group.findViewById(R.id.feedback_type_improvement)).setText(labels[1]);
        if ("bug".equals(selected)) group.check(R.id.feedback_type_error);
        else if ("enhancement".equals(selected)) group.check(R.id.feedback_type_improvement);
        addFormRow(parent, container);
        return group;
    }

    private static void setTypeEnabled(RadioGroup group, boolean enabled) {
        group.setEnabled(enabled);
        for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(enabled);
    }

    private static void setSelectorEnabled(MaterialAutoCompleteTextView selector, boolean enabled) {
        android.view.ViewParent parent = selector.getParent();
        while (parent instanceof View && !(parent instanceof TextInputLayout)) parent = parent.getParent();
        if (parent instanceof TextInputLayout) ((TextInputLayout) parent).setEnabled(enabled);
        selector.setEnabled(enabled);
    }

    private static MaterialAutoCompleteTextView dropdown(LinearLayout parent, int label, int items,
                                                         String[] values, String selected) {
        TextInputLayout layout = (TextInputLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_feedback_dropdown, parent, false);
        layout.setHint(parent.getContext().getString(label));
        layout.setExpandedHintEnabled(false);
        MaterialAutoCompleteTextView selector = (MaterialAutoCompleteTextView) layout.getEditText();
        String[] labels = parent.getResources().getStringArray(items);
        selector.setAdapter(new ArrayAdapter<>(selector.getContext(),
                com.google.android.material.R.layout.mtrl_auto_complete_simple_item, labels));
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selected)) selector.setText(labels[i], false);
        }
        addFormRow(parent, layout);
        return selector;
    }

    private static void addFormRow(LinearLayout parent, View row) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(12 * parent.getResources().getDisplayMetrics().density);
        parent.addView(row, params);
    }

    private static EditText field(LinearLayout parent, int label, String value, boolean multiline, int counterMaxLength) {
        TextInputLayout layout = new TextInputLayout(parent.getContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setHint(parent.getContext().getString(label));
        layout.setExpandedHintEnabled(false);
        layout.setCounterEnabled(true);
        layout.setCounterMaxLength(counterMaxLength);
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        edit.setMinLines(multiline ? 3 : 1); edit.setText(value);
        layout.addView(edit); addFormRow(parent, layout); return edit;
    }
}
