package com.liskovsoft.youtubeapi.browse.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelProfile;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads only channel-header and About fields, never inferring facts from video cards. */
public final class ChannelProfileParser {
    private ChannelProfileParser() { }

    public static ChannelProfile parseJson(String root, String about) {
        return parse(com.google.gson.JsonParser.parseString(root).getAsJsonObject(),
                about != null ? com.google.gson.JsonParser.parseString(about).getAsJsonObject() : null);
    }

    public static ChannelProfile parse(JsonObject root, JsonObject about) {
        JsonElement header = at(root, "header");
        JsonElement page = at(header, "pageHeaderRenderer");
        JsonElement view = at(page, "content", "pageHeaderViewModel");
        JsonElement legacy = at(header, "c4TabbedHeaderRenderer");
        JsonElement metadata = at(root, "metadata", "channelMetadataRenderer");
        String title = first(string(at(page, "pageTitle")), string(at(metadata, "title")), string(at(legacy, "title")));
        if (title == null) return null;
        String handle = null, subscribers = text(at(legacy, "subscriberCountText")), videos = text(at(legacy, "videosCountText"));
        JsonElement rows = at(view, "metadata", "contentMetadataViewModel", "metadataRows");
        if (rows != null && rows.isJsonArray()) {
            for (JsonElement row : rows.getAsJsonArray()) {
                JsonElement parts = at(row, "metadataParts");
                if (parts == null || !parts.isJsonArray()) continue;
                int index = 0;
                for (JsonElement part : parts.getAsJsonArray()) {
                    String value = text(at(part, "text"));
                    if (value != null && value.startsWith("@")) handle = value;
                    else if (value != null) {
                        if (index == 0) subscribers = value;
                        else if (index == 1) videos = value;
                        index++;
                    }
                }
            }
        }
        if (handle == null) handle = text(at(legacy, "channelHandleText"));
        String avatar = first(image(at(view, "image")), image(at(metadata, "avatar")), image(at(legacy, "avatar")));
        String banner = first(image(at(view, "banner")), image(at(legacy, "mobileBanner")), image(at(legacy, "banner")));
        String description = first(string(at(metadata, "description")),
                text(at(view, "description", "descriptionPreviewViewModel", "description")));
        List<ChannelProfile.Link> links = new ArrayList<>();
        collectLinks(about, links);
        JsonElement attribution = at(view, "attribution", "attributionViewModel");
        String firstUrl = string(at(find(attribution, "urlEndpoint"), "url"));
        addLink(links, text(at(attribution, "text")), firstUrl);
        boolean verified = header != null && (header.toString().contains("CHECK_CIRCLE_FILLED")
                || header.toString().contains("BADGE_STYLE_TYPE_VERIFIED"));
        JsonElement details = find(about, "aboutChannelViewModel");
        if (details == null) details = find(about, "channelAboutFullMetadataRenderer");
        return new ChannelProfile(title, handle, avatar, banner, subscribers, videos, description, verified, links,
                first(string(at(details, "canonicalChannelUrl")), string(at(metadata, "channelUrl"))),
                text(at(details, "country")), text(at(details, "joinedDateText")), text(at(details, "viewCountText")));
    }

    public static String aboutToken(JsonObject root) {
        return string(at(find(at(root, "header"), "continuationCommand"), "token"));
    }

    private static void collectLinks(JsonElement node, List<ChannelProfile.Link> links) {
        if (node == null || node.isJsonNull()) return;
        if (node.isJsonArray()) { for (JsonElement child : node.getAsJsonArray()) collectLinks(child, links); }
        else if (node.isJsonObject()) {
            JsonElement link = at(node, "channelExternalLinkViewModel");
            if (link != null) addLink(links, text(at(link, "title")),
                    string(at(find(link, "urlEndpoint"), "url")), image(at(link, "favicon")));
            for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject().entrySet()) collectLinks(entry.getValue(), links);
        }
    }

    private static void addLink(List<ChannelProfile.Link> links, String title, String raw) {
        addLink(links, title, raw, null);
    }

    private static void addLink(List<ChannelProfile.Link> links, String title, String raw, String iconUrl) {
        if (raw == null) return;
        String url = raw;
        try {
            URI uri = new URI(raw);
            if ("www.youtube.com".equals(uri.getHost()) && "/redirect".equals(uri.getPath()) && uri.getRawQuery() != null) {
                for (String pair : uri.getRawQuery().split("&")) if (pair.startsWith("q=")) {
                    url = URLDecoder.decode(pair.substring(2), "UTF-8");
                }
            }
            String scheme = new URI(url).getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return;
        } catch (Exception ignored) { return; }
        for (ChannelProfile.Link link : links) if (url.equals(link.url)) return;
        links.add(new ChannelProfile.Link(title != null ? title.trim() : url, url, iconUrl));
    }

    static JsonElement at(JsonElement root, String... path) {
        JsonElement value = root;
        for (String key : path) {
            if (value == null || !value.isJsonObject()) return null;
            value = value.getAsJsonObject().get(key);
        }
        return value != null && !value.isJsonNull() ? value : null;
    }

    private static JsonElement find(JsonElement root, String key) {
        if (root == null || root.isJsonNull()) return null;
        if (root.isJsonObject()) {
            if (root.getAsJsonObject().has(key)) return root.getAsJsonObject().get(key);
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                JsonElement found = find(entry.getValue(), key);
                if (found != null) return found;
            }
        } else if (root.isJsonArray()) for (JsonElement child : root.getAsJsonArray()) {
            JsonElement found = find(child, key);
            if (found != null) return found;
        }
        return null;
    }

    private static String image(JsonElement root) {
        JsonElement images = find(root, "sources");
        if (images == null) images = find(root, "thumbnails");
        String result = null;
        if (images != null && images.isJsonArray()) for (JsonElement image : images.getAsJsonArray()) {
            String url = string(at(image, "url"));
            if (url != null && url.startsWith("https://")) result = url;
        }
        return result;
    }

    private static String text(JsonElement value) {
        String direct = string(value);
        if (direct != null) return direct;
        String content = first(string(at(value, "content")), string(at(value, "simpleText")));
        if (content != null) return content;
        JsonElement runs = at(value, "runs");
        StringBuilder result = new StringBuilder();
        if (runs != null && runs.isJsonArray()) for (JsonElement run : runs.getAsJsonArray()) {
            String runText = string(at(run, "text"));
            if (runText != null) result.append(runText);
        }
        return result.length() == 0 ? null : result.toString();
    }

    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return null;
    }
}
