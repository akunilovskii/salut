package com.newtube.mobile.watchlater;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlaylistOrderStoreTest {
    @Test public void movesDownWithoutRemovingOrAddingVideos() {
        FakeTransport transport = new FakeTransport("a", "b", "c", "d");
        new PlaylistOrderStore(transport).move("a", "c");
        assertEquals(Arrays.asList("b", "c", "a", "d"), transport.videos);
        assertEquals("ACTION_MOVE_VIDEO_AFTER", transport.lastAction.get("action").getAsString());
        assertEquals("set-a", transport.lastAction.get("setVideoId").getAsString());
        assertEquals("set-c", transport.lastAction.get("movedSetVideoIdPredecessor").getAsString());
        assertEquals(2, transport.reads);
    }

    @Test public void movesToTheFirstPositionWithoutAnAnchor() {
        FakeTransport transport = new FakeTransport("a", "b", "c");
        new PlaylistOrderStore(transport).move("c", null);
        assertEquals(Arrays.asList("c", "a", "b"), transport.videos);
        assertFalse(transport.lastAction.has("movedSetVideoIdPredecessor"));
    }

    @Test public void movesToTheLastPositionAfterItsPredecessor() {
        FakeTransport transport = new FakeTransport("a", "b", "c");
        new PlaylistOrderStore(transport).move("a", "c");
        assertEquals(Arrays.asList("b", "c", "a"), transport.videos);
    }

    @Test public void followsContinuationPagesForBothReads() {
        FakeTransport transport = new FakeTransport("a", "b", "c", "d");
        transport.pageSize = 2;
        new PlaylistOrderStore(transport).move("d", "b");
        assertEquals(Arrays.asList("a", "b", "d", "c"), transport.videos);
        assertEquals(4, transport.reads);
    }

    @Test public void rejectsAnHttpSuccessContainingAnApiFailure() {
        FakeTransport transport = new FakeTransport("a", "b");
        transport.reject = true;
        assertThrows(IllegalStateException.class, () -> new PlaylistOrderStore(transport).move("b", null));
        assertEquals(Arrays.asList("a", "b"), transport.videos);
    }

    @Test public void verifiesTheSavedPositionAfterAnApparentlySuccessfulEdit() {
        FakeTransport transport = new FakeTransport("a", "b");
        transport.ignoreEdit = true;
        assertThrows(IllegalStateException.class, () -> new PlaylistOrderStore(transport).move("b", null));
    }

    @Test public void missingEntryCannotEditThePlaylist() {
        FakeTransport transport = new FakeTransport("a", "b");
        assertThrows(IllegalStateException.class, () -> new PlaylistOrderStore(transport).move("missing", "a"));
        assertNull(transport.lastAction);
    }

    @Test public void duplicateVideoIdsCannotMoveTheWrongOccurrence() {
        FakeTransport transport = new FakeTransport("a", "a", "b");
        assertThrows(IllegalStateException.class, () -> new PlaylistOrderStore(transport).move("a", "b"));
        assertNull(transport.lastAction);
    }

    @Test public void selfRelativeMovesCannotEditThePlaylist() {
        FakeTransport transport = new FakeTransport("a", "b");
        assertThrows(IllegalArgumentException.class, () -> new PlaylistOrderStore(transport).move("a", "a"));
        assertEquals(0, transport.reads);
    }

    @Test public void authenticatedTvTilesResolveEntryIdsFromTheirCommands() {
        FakeTransport transport = new FakeTransport("a", "b", "c");
        transport.tv = true;
        List<String> saved = new PlaylistOrderStore(transport).move("c", "a");
        assertEquals(Arrays.asList("a", "c", "b"), saved);
        assertEquals("set-c", transport.lastAction.get("setVideoId").getAsString());
    }

    @Test public void tvContinuationsRetainGlobalPlaybackPositions() {
        FakeTransport transport = new FakeTransport("a", "b", "c", "d");
        transport.tv = true;
        transport.pageSize = 2;
        assertEquals(Arrays.asList("a", "c", "d", "b"), new PlaylistOrderStore(transport).move("b", "d"));
    }

    private static final class FakeTransport implements PlaylistOrderStore.Transport {
        final List<String> videos;
        int reads;
        int pageSize = Integer.MAX_VALUE;
        boolean reject;
        boolean ignoreEdit;
        boolean tv;
        JsonObject lastAction;

        FakeTransport(String... ids) { videos = new ArrayList<>(Arrays.asList(ids)); }

        @Override public JsonObject browse(JsonObject query) {
            reads++;
            int start = query.has("continuation") ? Integer.parseInt(query.get("continuation").getAsString()) : 0;
            int end = (int) Math.min((long) start + pageSize, videos.size());
            JsonArray contents = new JsonArray();
            for (String id : videos.subList(start, end)) {
                JsonObject video = new JsonObject();
                video.addProperty("videoId", id);
                video.addProperty("setVideoId", "set-" + id);
                JsonObject wrapper = new JsonObject();
                if (tv) {
                    JsonObject endpoint = new JsonObject();
                    endpoint.addProperty("videoId", id);
                    JsonObject tile = new JsonObject();
                    tile.add("watchEndpoint", endpoint);
                    JsonObject edit = new JsonObject();
                    edit.addProperty("setVideoId", "set-" + id);
                    tile.add("playlistEditEndpoint", edit);
                    wrapper.add("tileRenderer", tile);
                } else {
                    wrapper.add("playlistVideoRenderer", video);
                }
                contents.add(wrapper);
            }
            if (end < videos.size()) {
                JsonObject command = new JsonObject();
                command.addProperty(tv ? "continuation" : "token", String.valueOf(end));
                JsonObject wrapper = new JsonObject();
                wrapper.add(tv ? "nextContinuationData" : "continuationCommand", command);
                contents.add(wrapper);
            }
            JsonObject response = new JsonObject();
            response.add("contents", contents);
            return response;
        }

        @Override public JsonObject edit(JsonObject query) {
            assertEquals("WL", query.get("playlistId").getAsString());
            assertEquals(1, query.getAsJsonArray("actions").size());
            lastAction = query.getAsJsonArray("actions").get(0).getAsJsonObject();
            JsonObject response = new JsonObject();
            response.addProperty("status", reject ? "STATUS_FAILED" : "STATUS_SUCCEEDED");
            if (!reject && !ignoreEdit) {
                String moved = lastAction.get("setVideoId").getAsString().substring(4);
                videos.remove(moved);
                int index = lastAction.has("movedSetVideoIdPredecessor")
                        ? videos.indexOf(lastAction.get("movedSetVideoIdPredecessor").getAsString().substring(4)) + 1 : 0;
                videos.add(index, moved);
            }
            return response;
        }
    }
}
