package com.newtube.mobile.feedback;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Private message draft and immutable retry envelope. No account or device identifiers. */
public final class FeedbackDraft {
    public String kind = "", screen = "", description = "", steps = "", expected = "";
    private String reportId = "", attemptedPayload = "";
    private final Session session;
    private String savedFields;

    /** Process memory only; a different Android task starts with an empty draft. */
    static final class Session {
        private static int activeTaskId = Integer.MIN_VALUE;
        private static Session active;
        private String contents = "{}";

        static synchronized Session forTask(int taskId) {
            if (active == null || activeTaskId != taskId) {
                activeTaskId = taskId;
                active = new Session();
            }
            return active;
        }

        void clear() { contents = "{}"; }
    }

    FeedbackDraft(Session session) {
        this.session = session;
        try {
            JSONObject j = new JSONObject(session.contents);
            kind = j.optString("kind"); screen = j.optString("screen");
            description = j.optString("description");
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
            session.contents = j.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private JSONObject fields(boolean draft) throws Exception {
        JSONObject j = new JSONObject().put("kind", kind).put("screen", screen)
                .put("description", description);
        if (draft || "bug".equals(kind)) j.put("steps", steps).put("expected", expected);
        if (!draft && steps.isEmpty()) j.remove("steps");
        if (!draft && expected.isEmpty()) j.remove("expected");
        return j;
    }

    public boolean canSend() { return ("bug".equals(kind) || "enhancement".equals(kind)) && !description.trim().isEmpty(); }

    public String prepare(String app, String android) throws Exception {
        save();
        // An unchanged draft retries the original envelope, including its version metadata.
        if (!attemptedPayload.isEmpty() && !reportId.isEmpty()) {
            return attemptedPayload;
        }
        if (!canSend()) throw new IllegalArgumentException("required");
        if (screen.length() > 80 || description.length() > 6000
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

    public void clear() { session.clear(); }
}
