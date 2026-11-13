package com.liskovsoft.mediaserviceinterfaces.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Public channel details, independent of account-specific subscription state. */
public final class ChannelProfile {
    public final String title, handle, avatarUrl, bannerUrl, subscribers, videos, description;
    public final boolean verified;
    public final List<Link> links;
    public final String channelUrl, country, joined, views;

    public ChannelProfile(String title, String handle, String avatarUrl, String bannerUrl,
            String subscribers, String videos, String description, boolean verified, List<Link> links) {
        this(title, handle, avatarUrl, bannerUrl, subscribers, videos, description, verified, links, null, null, null, null);
    }

    public ChannelProfile(String title, String handle, String avatarUrl, String bannerUrl,
            String subscribers, String videos, String description, boolean verified, List<Link> links,
            String channelUrl, String country, String joined, String views) {
        this.title = title;
        this.handle = handle;
        this.avatarUrl = avatarUrl;
        this.bannerUrl = bannerUrl;
        this.subscribers = subscribers;
        this.videos = videos;
        this.description = description;
        this.verified = verified;
        this.links = Collections.unmodifiableList(new ArrayList<>(links));
        this.channelUrl = channelUrl; this.country = country; this.joined = joined; this.views = views;
    }

    public static final class Link {
        public final String title, url, iconUrl;
        public Link(String title, String url) { this(title, url, null); }
        public Link(String title, String url, String iconUrl) { this.title = title; this.url = url; this.iconUrl = iconUrl; }
    }
}
