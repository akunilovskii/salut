package com.newtube.mobile.feedback;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Private message draft and immutable retry envelope. No account or device identifiers. */
public final class FeedbackDraft {
    public String kind = "", screen = "Other", function = "", description = "", steps = "", expected = "";
    private String reportId = "", attemptedPayload = "";
    private final SharedPreferences prefs;
    private String savedFields;

    public FeedbackDraft(SharedPreferences prefs) {
        this.prefs = prefs;
        try {
            JSONObject j = new JSONObject(prefs.getString("draft", "{}"));
            kind = j.optString("kind"); screen = j.optString("screen", "Other");
            function = j.optString("function"); description = j.optString("description");
            steps = j.optString("steps"); expected = j.optString("expected");
            reportId = j.optString("reportId"); attemptedPayload = j.optString("attemptedPayload");
        } catch (Exception ignored) { }
        try { savedFields = fields(true).toString(); } catch (Exception ignored) { }
    }

    public void save() {
        try {
            JSONObject j = fields(true);
            String current = j.toString();
            if (!current.equals(savedFields)) { reportId = ""; attemptedPayload = ""; }
            savedFields = current;
            j.put("reportId", reportId).put("attemptedPayload", attemptedPayload);
            prefs.edit().putString("draft", j.toString()).apply();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private JSONObject fields(boolean draft) throws Exception {
        JSONObject j = new JSONObject().put("kind", kind).put("screen", screen)
                .put("function", function).put("description", description);
        if (draft || "bug".equals(kind)) j.put("steps", steps).put("expected", expected);
        if (!draft && function.isEmpty()) j.remove("function");
        if (!draft && steps.isEmpty()) j.remove("steps");
        if (!draft && expected.isEmpty()) j.remove("expected");
        return j;
    }

    public boolean canSend() { return ("bug".equals(kind) || "enhancement".equals(kind)) && !description.trim().isEmpty(); }

    public String prepare(String app, String android) throws Exception {
        save();
        // An unchanged draft retries the original envelope, including its version metadata.
        if (!attemptedPayload.isEmpty() && !reportId.isEmpty()) {
            JSONObject previous = new JSONObject(attemptedPayload);
            // Read drafts written before the full envelope was persisted.
            if (!previous.has("reportId")) {
                attemptedPayload = previous.put("reportId", reportId).toString();
                save();
            }
            return attemptedPayload;
        }
        if (!canSend() || screen.trim().isEmpty()) throw new IllegalArgumentException("required");
        if (screen.length() > 80 || function.length() > 200 || description.length() > 6000
                || ("bug".equals(kind) && (steps.length() > 4000 || expected.length() > 4000))
                || app.length() > 64 || android.length() > 32) throw new IllegalArgumentException("length");
        String content = fields(false).put("schemaVersion", 1).put("appVersion", app)
                .put("androidVersion", android).toString();
        reportId = UUID.randomUUID().toString();
        JSONObject body = new JSONObject(content).put("reportId", reportId);
        String raw = body.toString();
        if (raw.getBytes(StandardCharsets.UTF_8).length > 32768) throw new IllegalArgumentException("bytes");
        attemptedPayload = raw;
        save();
        return raw;
    }

    public static boolean accepted(int code, String receipt, String payload) {
        try {
            JSONObject j = new JSONObject(receipt);
            return code == 202 && "accepted".equals(j.optString("status"))
                    && new JSONObject(payload).getString("reportId").equals(j.getString("reportId"));
        } catch (Exception e) { return false; }
    }

    public void clear() { prefs.edit().remove("draft").apply(); }
}
