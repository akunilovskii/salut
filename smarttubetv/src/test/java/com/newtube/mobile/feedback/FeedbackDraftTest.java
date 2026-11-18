package com.newtube.mobile.feedback;

import android.app.Application;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class FeedbackDraftTest {
    private FeedbackDraft.Session session;
    private FeedbackDraft draft;
    @Before public void setup() {
        session = new FeedbackDraft.Session();
        draft = new FeedbackDraft(session);
    }
    private void valid() { draft.kind = "bug"; draft.description = "A failure"; draft.save(); }
    @Test public void screenIsOptionalAndExplicitChoiceSurvivesRecreation() throws Exception {
        assertEquals("", draft.screen);
        valid();
        assertEquals("", new JSONObject(draft.prepare("1", "17")).getString("screen"));
        draft.screen = "Player"; draft.save();
        assertEquals("Player", new FeedbackDraft(session).screen);
        draft.screen = ""; draft.save();
        assertEquals("", new FeedbackDraft(session).screen);
    }
    @Test public void taskSessionRetainsDraftUntilTaskChanges() throws Exception {
        FeedbackDraft.Session original = FeedbackDraft.Session.forTask(100);
        FeedbackDraft first = new FeedbackDraft(original);
        first.kind = "bug"; first.description = "Session report";
        String payload = first.prepare("1", "17");
        assertSame(original, FeedbackDraft.Session.forTask(100));
        assertEquals(payload, new FeedbackDraft(FeedbackDraft.Session.forTask(100)).prepare("2", "18"));
        assertEquals("", new FeedbackDraft(FeedbackDraft.Session.forTask(101)).description);
        assertEquals("", new FeedbackDraft(FeedbackDraft.Session.forTask(100)).description);
    }
    @Test public void freshProcessSessionDoesNotRestoreDraft() {
        valid();
        assertEquals("", new FeedbackDraft(new FeedbackDraft.Session()).description);
    }
    @Test public void requiresExplicitTypeAndDescription() throws Exception {
        assertFalse(draft.canSend()); draft.description = "Text"; assertFalse(draft.canSend());
        draft.kind = "enhancement"; assertTrue(draft.canSend());
        draft.description = " \n"; assertFalse(draft.canSend());
    }
    @Test public void payloadWhitelistAndHiddenFields() throws Exception {
        valid(); draft.steps = "secret steps"; draft.expected = "result"; draft.kind = "enhancement"; draft.save();
        JSONObject j = new JSONObject(draft.prepare("1.18.0", "16"));
        assertFalse(j.has("steps")); assertFalse(j.has("expected")); assertFalse(j.has("function"));
        assertEquals(7, j.length()); assertEquals(1, j.getInt("schemaVersion"));
        draft.kind = "bug"; draft.save();
        assertEquals("secret steps", new JSONObject(draft.prepare("1", "16")).getString("steps"));
    }
    @Test public void draftAndExactRetrySurviveRecreation() throws Exception {
        valid(); String first = draft.prepare("1", "16");
        FeedbackDraft restored = new FeedbackDraft(session);
        assertEquals("A failure", restored.description);
        assertEquals(first, restored.prepare("1", "16"));
        restored.description = "Edited"; restored.save();
        restored.description = "A failure"; restored.save();
        assertNotEquals(new JSONObject(first).getString("reportId"),
                new JSONObject(restored.prepare("1", "16")).getString("reportId"));
    }
    @Test public void unchangedRetryKeepsEnvelopeAcrossAppAndAndroidUpdates() throws Exception {
        valid();
        String first = draft.prepare("1.18.0", "15");
        FeedbackDraft restored = new FeedbackDraft(session);
        assertEquals(first, restored.prepare("1.19.0", "16"));
        restored.description = "Edited after update";
        String edited = restored.prepare("1.19.0", "16");
        JSONObject next = new JSONObject(edited);
        assertNotEquals(new JSONObject(first).getString("reportId"), next.getString("reportId"));
        assertEquals("1.19.0", next.getString("appVersion"));
        assertEquals("16", next.getString("androidVersion"));
        assertEquals(edited, new FeedbackDraft(session).prepare("1.20.0", "17"));
    }
    @Test public void onlyMatchingAcceptedReceiptClearsDraft() throws Exception {
        valid(); String body = draft.prepare("1", "16");
        String id = new JSONObject(body).getString("reportId");
        String receipt = new JSONObject().put("reportId", id).put("status", "accepted").toString();
        assertTrue(FeedbackDraft.accepted(202, receipt, body));
        for (int code : new int[]{200,400,409,413,429,500}) assertFalse(FeedbackDraft.accepted(code, receipt, body));
        assertFalse(FeedbackDraft.accepted(202, "{}", body));
        assertFalse(FeedbackDraft.accepted(202, receipt.replace(id, "other"), body));
        assertEquals("A failure", new FeedbackDraft(session).description);
        draft.clear(); assertEquals("", new FeedbackDraft(session).description);
    }
    @Test public void enforcesFieldAndUtf8BodyLimits() throws Exception {
        valid(); draft.description = new String(new char[6001]).replace('\0','x');
        try { draft.prepare("1", "16"); fail(); } catch (IllegalArgumentException expected) { }
        draft.description = new String(new char[6000]).replace('\0','界');
        draft.steps = new String(new char[4000]).replace('\0','界');
        draft.expected = draft.steps;
        try { draft.prepare("1", "16"); fail(); } catch (IllegalArgumentException expected) { }
        draft.kind = "enhancement"; draft.save();
        assertFalse(new JSONObject(draft.prepare("1", "16")).has("steps"));
    }
}
