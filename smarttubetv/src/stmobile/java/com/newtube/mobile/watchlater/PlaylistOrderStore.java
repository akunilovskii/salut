package com.newtube.mobile.watchlater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.liskovsoft.googlecommon.common.converters.gson.WithGson;
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders;
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.youtubeapi.common.helpers.PostDataHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/** Saves relative positions using YouTube's playlist-entry IDs, never remove/add operations. */
public final class PlaylistOrderStore {
    public interface Transport {
        JsonObject browse(JsonObject query);
        JsonObject edit(JsonObject query);
    }

    @WithGson
    public interface Api {
        @Headers({"Content-Type: application/json", "User-Agent: " + DefaultHeaders.USER_AGENT_TV,
                "Referer: https://www.youtube.com/tv"})
        @POST("https://www.youtube.com/youtubei/v1/browse")
        Call<JsonObject> browse(@Body String query);

        @Headers({"Content-Type: application/json", "User-Agent: " + DefaultHeaders.USER_AGENT_TV,
                "Referer: https://www.youtube.com/tv"})
        @POST("https://www.youtube.com/youtubei/v1/browse/edit_playlist")
        Call<JsonObject> edit(@Body String query);
    }

    private final Transport mTransport;

    public PlaylistOrderStore(Transport transport) {
        mTransport = transport;
    }

    public static PlaylistOrderStore forAccount(Account account) {
        if (account == null || account.isEmpty()) throw new IllegalArgumentException("A saved account is required");
        Api api = RetrofitHelper.create(Api.class);
        return new PlaylistOrderStore(new Transport() {
            private String query(JsonObject data) {
                // Account IDs in legacy records are not unique. Keep the exact selected instance.
                if (Thread.currentThread().isInterrupted()
                        || MediaServiceManager.instance().getSelectedAccount() != account) {
                    throw new IllegalStateException("The selected account changed");
                }
                String json = data.toString();
                return PostDataHelper.createQueryTV(json.substring(1, json.length() - 1));
            }

            @Override public JsonObject browse(JsonObject data) {
                return RetrofitHelper.getWithErrors(api.browse(query(data)));
            }

            @Override public JsonObject edit(JsonObject data) {
                return RetrofitHelper.getWithErrors(api.edit(query(data)));
            }
        });
    }

    public List<String> move(String videoId, String predecessorVideoId) {
        if (videoId == null || videoId.equals(predecessorVideoId)) {
            throw new IllegalArgumentException("A move requires distinct playlist entries");
        }
        List<Entry> entries = loadUntil(videoId, predecessorVideoId);
        String moved = uniqueEntry(entries, videoId).setId;
        String predecessor = predecessorVideoId == null ? null : uniqueEntry(entries, predecessorVideoId).setId;
        JsonObject action = new JsonObject();
        action.addProperty("action", "ACTION_MOVE_VIDEO_AFTER");
        action.addProperty("setVideoId", moved);
        if (predecessor != null) {
            action.addProperty("movedSetVideoIdPredecessor", predecessor);
        }
        JsonArray actions = new JsonArray();
        actions.add(action);
        JsonObject request = new JsonObject();
        request.addProperty("playlistId", "WL");
        request.add("actions", actions);
        requireSuccess(mTransport.edit(request));

        // Verify a fresh server response rather than treating HTTP 200 as a successful edit.
        List<Entry> saved = loadUntil(videoId, predecessorVideoId);
        int position = saved.indexOf(uniqueEntry(saved, videoId));
        String actualPredecessor = position == 0 ? null : saved.get(position - 1).videoId;
        if (!java.util.Objects.equals(actualPredecessor, predecessorVideoId)) {
            throw new IllegalStateException("YouTube did not save the playlist position");
        }
        List<String> order = new ArrayList<>();
        for (Entry entry : saved) order.add(entry.videoId);
        return order;
    }

    private List<Entry> loadUntil(String moved, String predecessor) {
        List<Entry> entries = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        JsonObject query = new JsonObject();
        query.addProperty("browseId", "VLWL");
        while (true) {
            JsonObject response = mTransport.browse(query);
            requireSuccess(response);
            collectEntries(response, entries);
            if (hasEntry(entries, moved) && (predecessor == null || hasEntry(entries, predecessor))) {
                return entries;
            }
            String token = continuation(response);
            if (token == null || !visited.add(token)) {
                throw new IllegalStateException("The playlist entries are no longer available");
            }
            query = new JsonObject();
            query.addProperty("continuation", token);
        }
    }

    private static boolean hasEntry(List<Entry> entries, String id) {
        for (Entry entry : entries) if (id.equals(entry.videoId)) return true;
        return false;
    }

    private static Entry uniqueEntry(List<Entry> entries, String id) {
        Entry found = null;
        for (Entry entry : entries) {
            if (id.equals(entry.videoId)) {
                if (found != null) throw new IllegalStateException("Ambiguous playlist entry");
                found = entry;
            }
        }
        if (found == null || found.setId == null) throw new IllegalStateException("Missing playlist entry ID");
        return found;
    }

    private static void collectEntries(JsonElement value, List<Entry> entries) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) collectEntries(child, entries);
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String renderer = object.has("tileRenderer") ? "tileRenderer"
                    : object.has("playlistVideoRenderer") ? "playlistVideoRenderer" : null;
            if (renderer != null) {
                JsonObject video = object.getAsJsonObject(renderer);
                String id = deepString(video, "videoId");
                if (id != null) entries.add(new Entry(id, deepString(video, "setVideoId")));
            } else {
                for (Map.Entry<String, JsonElement> child : object.entrySet()) collectEntries(child.getValue(), entries);
            }
        }
    }

    private static String continuation(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) {
                String token = continuation(child);
                if (token != null) return token;
            }
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("continuationCommand")) return string(object.getAsJsonObject("continuationCommand"), "token");
            if (object.has("nextContinuationData")) return string(object.getAsJsonObject("nextContinuationData"), "continuation");
            for (Map.Entry<String, JsonElement> child : object.entrySet()) {
                String token = continuation(child.getValue());
                if (token != null) return token;
            }
        }
        return null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static String deepString(JsonElement value, String key) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) {
            String result = deepString(child, key);
            if (result != null) return result;
        }
        if (value.isJsonObject()) {
            String direct = string(value.getAsJsonObject(), key);
            if (direct != null) return direct;
            for (Map.Entry<String, JsonElement> child : value.getAsJsonObject().entrySet()) {
                String result = deepString(child.getValue(), key);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void requireSuccess(JsonObject response) {
        if (response == null || response.has("error")
                || (response.has("status") && !"STATUS_SUCCEEDED".equals(string(response, "status")))) {
            throw new IllegalStateException("YouTube rejected the playlist request");
        }
    }

    private static final class Entry {
        final String videoId;
        final String setId;
        Entry(String videoId, String setId) { this.videoId = videoId; this.setId = setId; }
    }
}
