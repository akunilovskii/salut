package com.newtube.mobile.feedback;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
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

    private AlertDialog dialog;
    private Disposable request;
    private Call call;
    private boolean busy;
    private boolean completed;

    public void close() {
        if (call != null) call.cancel();
        if (request != null) request.dispose();
        if (dialog != null) dialog.dismiss();
    }

    public void show(Activity activity, String category) {
        if (dialog != null && dialog.isShowing()) return;
        completed = false;
        FeedbackDraft draft = new FeedbackDraft(activity.getSharedPreferences("feedback_draft", Context.MODE_PRIVATE));
        if (!activity.getSharedPreferences("feedback_draft", Context.MODE_PRIVATE).contains("draft")) draft.screen = category;
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
        note.setText(activity.getString(R.string.feedback_note) + "\nSalut " + app + " · Android " + Build.VERSION.RELEASE);
        content.addView(note);
        Spinner type = new Spinner(context);
        type.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                activity.getResources().getStringArray(R.array.feedback_types)));
        type.setSelection("bug".equals(draft.kind) ? 1 : "enhancement".equals(draft.kind) ? 2 : 0);
        content.addView(type);
        TextView screenLabel = new TextView(context);
        screenLabel.setText(R.string.feedback_screen); content.addView(screenLabel);
        Spinner screen = new Spinner(context);
        String[] categories = {"Home", "Subscriptions", "Watch later", "Playlist", "Channel", "Search", "Player", "Settings", "Other"};
        screen.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                activity.getResources().getStringArray(R.array.feedback_screens)));
        for (int i = 0; i < categories.length; i++) if (categories[i].equals(draft.screen)) screen.setSelection(i);
        screen.setContentDescription(activity.getString(R.string.feedback_screen));
        content.addView(screen);
        EditText function = field(content, R.string.feedback_function, draft.function, false);
        EditText description = field(content, R.string.feedback_description, draft.description, true);
        EditText steps = field(content, R.string.feedback_steps, draft.steps, true);
        EditText expected = field(content, R.string.feedback_expected, draft.expected, true);
        ScrollView scroll = new ScrollView(context); scroll.addView(content);
        dialog = new MaterialAlertDialogBuilder(context).setTitle(R.string.feedback_title).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.feedback_send, null).create();
        dialog.show();
        Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        send.setBackground(null);
        send.setTextColor(androidx.core.content.ContextCompat.getColorStateList(context, R.color.mobile_dialog_button_text));
        Runnable update = () -> {
            draft.kind = type.getSelectedItemPosition() == 1 ? "bug" : type.getSelectedItemPosition() == 2 ? "enhancement" : "";
            draft.screen = categories[screen.getSelectedItemPosition()];
            draft.function = function.getText().toString(); draft.description = description.getText().toString();
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
        for (EditText edit : new EditText[]{function, description, steps, expected}) edit.addTextChangedListener(watcher);
        AdapterView.OnItemSelectedListener selection = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) { update.run(); }
            public void onNothingSelected(AdapterView<?> p) { }
        };
        type.setOnItemSelectedListener(selection); screen.setOnItemSelectedListener(selection);
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
            for (EditText edit : new EditText[]{function, description, steps, expected}) edit.setEnabled(false);
            type.setEnabled(false); screen.setEnabled(false); update.run();
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
                } else { restore(type, screen, function, description, steps, expected); update.run();
                    MobileSnackbar.show(activity, result); }
            }, error -> {
                if (activity.isDestroyed() || activity.isFinishing() || !dialog.isShowing()) return;
                restore(type, screen, function, description, steps, expected); update.run();
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

    private void restore(Spinner type, Spinner screen, EditText... fields) {
        busy = false; type.setEnabled(true); screen.setEnabled(true);
        for (EditText field : fields) field.setEnabled(true);
    }

    private static EditText field(LinearLayout parent, int label, String value, boolean multiline) {
        TextInputLayout layout = new TextInputLayout(parent.getContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setHint(parent.getContext().getString(label));
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | (multiline ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        edit.setMinLines(multiline ? 3 : 1); edit.setText(value);
        layout.addView(edit); parent.addView(layout); return edit;
    }
}
