package com.newtube.mobile.ui.dialog;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Xml;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/** Prepares a user-reviewed request for installed assistants, without sending a chat message. */
public final class AiVideoShare {
    private static final int MAX_TRANSCRIPT = 120_000;
    private AiVideoShare() { }

    public static void show(Context context, Video video) {
        if (!(context instanceof Activity) || ((Activity) context).isFinishing()) return;
        AlertDialog loading = new MaterialAlertDialogBuilder(context).setTitle(R.string.mobile_ai_summary)
                .setMessage(R.string.mobile_ai_loading).setView(new ProgressBar(context))
                .setNegativeButton(android.R.string.cancel, null).create();
        loading.show();
        Disposable request = Observable.fromCallable(() -> transcript(video.videoId))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(text -> {
                    if (!loading.isShowing() || ((Activity) context).isFinishing()
                            || ((Activity) context).isDestroyed()) return;
                    loading.dismiss();
                    review(context, video, text);
                }, error -> {
                    if (loading.isShowing() && !((Activity) context).isFinishing()
                            && !((Activity) context).isDestroyed()) {
                        loading.dismiss();
                        review(context, video, "");
                    }
                });
        loading.setOnCancelListener(dialog -> request.dispose());
        loading.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
            request.dispose();
            loading.dismiss();
        });
    }

    static AlertDialog review(Context context, Video video, String transcript) {
        Context formContext = new androidx.appcompat.view.ContextThemeWrapper(context, R.style.MobileAlertDialog);
        LinearLayout content = new LinearLayout(formContext);
        content.setOrientation(LinearLayout.VERTICAL);
        int inset = Math.round(24 * context.getResources().getDisplayMetrics().density);
        content.setPadding(inset, 0, inset, 0);
        TextInputLayout field = new TextInputLayout(formContext, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        field.setHint(context.getString(R.string.mobile_ai_instruction_label));
        EditText instruction = new TextInputEditText(field.getContext());
        instruction.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        instruction.setText(R.string.mobile_ai_instruction);
        instruction.setMinLines(3);
        instruction.setMaxLines(5);
        instruction.setTextSize(16);
        field.addView(instruction);
        content.addView(field);
        TextView note = new TextView(formContext);
        note.setText(transcript.isEmpty() ? R.string.mobile_ai_link_only : transcript.length() > MAX_TRANSCRIPT
                ? R.string.mobile_ai_shortened : R.string.mobile_ai_with_subtitles);
        note.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.mobile_color_on_surface_secondary));
        note.setPadding(0, inset / 2, 0, inset / 2);
        content.addView(note);
        ScrollView scroll = new ScrollView(formContext);
        scroll.addView(content);
        AiShareTargetStore store = new AiShareTargetStore(context);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setTitle(R.string.mobile_ai_summary).setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.mobile_ai_choose_app, null)
                .setPositiveButton(R.string.mobile_ai_send, null).create();
        dialog.show();
        Button sendButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        sendButton.setBackground(null);
        sendButton.setTextColor(androidx.core.content.ContextCompat.getColorStateList(
                context, R.color.mobile_dialog_button_text));
        Runnable update = () -> {
            ResolveInfo target = store.restore(context);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(target == null
                    ? context.getString(R.string.mobile_ai_choose_app) : AiShareTargetStore.label(context, target));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(target != null);
        };
        update.run();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            List<ResolveInfo> targets = AiShareTargetStore.available(context);
            if (targets.isEmpty()) {
                new MaterialAlertDialogBuilder(context).setMessage(R.string.mobile_ai_no_apps)
                        .setPositiveButton(android.R.string.ok, null).show();
                update.run();
                return;
            }
            CharSequence[] labels = new CharSequence[targets.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = AiShareTargetStore.label(context, targets.get(i));
            new MaterialAlertDialogBuilder(context).setTitle(R.string.mobile_ai_choose_app)
                    .setItems(labels, (picker, which) -> {
                        store.remember(targets.get(which));
                        update.run();
                    }).setNegativeButton(android.R.string.cancel, null).show();
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            ResolveInfo target = store.restore(context);
            if (target == null) { update.run(); return; }
            Intent send = AiShareTargetStore.sendIntent(requestText(instruction.getText().toString(),
                    video.getTitle(), video.videoId, transcript)).setComponent(AiShareTargetStore.component(target));
            try {
                context.startActivity(send);
                dialog.dismiss();
            } catch (ActivityNotFoundException | SecurityException error) {
                store.clear();
                update.run();
                note.setText(R.string.mobile_ai_app_unavailable);
            }
        });
        return dialog;
    }

    static String requestText(String instruction, String title, String videoId, String transcript) {
        String text = instruction.trim() + "\n\n" + (title != null ? title + "\n" : "")
                + "https://www.youtube.com/watch?v=" + Uri.encode(videoId);
        if (!transcript.isEmpty()) {
            text += "\n\nTranscript:\n" + transcript.substring(0, Math.min(MAX_TRANSCRIPT, transcript.length()));
            if (transcript.length() > MAX_TRANSCRIPT) text += "\n[Transcript shortened; summarize only the supplied text.]";
        } else {
            text += "\n\nNo transcript is attached. If you cannot access the video, say so instead of guessing.";
        }
        return text;
    }

    private static String transcript(String id) throws Exception {
        MediaSubtitle track = YouTubeServiceManager.instance().getMediaItemService()
                .getFormatInfo(id).getOriginalSubtitle();
        if (track == null) return "";
        HttpUrl url = HttpUrl.parse(track.getBaseUrl());
        if (url == null || !"https".equals(url.scheme())) return "";
        Request request = new Request.Builder().url(url.newBuilder().setQueryParameter("fmt", "json3").build()).build();
        try (Response response = OkHttpManager.instance().getClient().newBuilder()
                .callTimeout(25, TimeUnit.SECONDS).build().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return "";
            return parseTranscript(response.body().string());
        }
    }

    static String parseTranscript(String raw) throws Exception {
        StringBuilder text = new StringBuilder();
        if (raw.trim().startsWith("{")) {
            JSONArray events = new JSONObject(raw).optJSONArray("events");
            if (events == null) return "";
            for (int i = 0; i < events.length(); i++) {
                JSONArray segments = events.getJSONObject(i).optJSONArray("segs");
                if (segments == null) continue;
                for (int j = 0; j < segments.length(); j++) text.append(segments.getJSONObject(j).optString("utf8"));
                text.append('\n');
            }
        } else if (raw.trim().startsWith("<")) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(raw));
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.TEXT) text.append(parser.getText()).append(' ');
            }
        }
        return text.toString()
                .replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }
}
