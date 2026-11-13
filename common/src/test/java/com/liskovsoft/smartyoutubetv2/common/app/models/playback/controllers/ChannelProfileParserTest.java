package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import static org.junit.Assert.*;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelProfile;
import com.liskovsoft.youtubeapi.browse.v2.ChannelProfileParser;
import org.junit.Test;

public class ChannelProfileParserTest {
    @Test public void fullAboutRetainsFactsAndServiceIcons() {
        ChannelProfile profile = ChannelProfileParser.parseJson("{\"metadata\":{\"channelMetadataRenderer\":{\"title\":\"Name\"}}}", """
                {"aboutChannelViewModel":{"canonicalChannelUrl":"https://www.youtube.com/@test","country":"United States",
                  "joinedDateText":{"content":"Joined Jul 20, 2011"},"viewCountText":"13,264,446 views",
                  "links":[{"channelExternalLinkViewModel":{"title":{"content":"Discord"},
                    "favicon":{"sources":[{"url":"https://example.com/discord.png"}]},
                    "urlEndpoint":{"url":"https://discord.gg/test"}}}]}}
                """);
        assertEquals("United States", profile.country);
        assertEquals("Joined Jul 20, 2011", profile.joined);
        assertEquals("13,264,446 views", profile.views);
        assertEquals("https://www.youtube.com/@test", profile.channelUrl);
        assertEquals("https://example.com/discord.png", profile.links.get(0).iconUrl);
    }
    @Test public void modernHeaderProvidesOnlyActualChannelFacts() {
        ChannelProfile profile = ChannelProfileParser.parseJson("""
            {"header":{"pageHeaderRenderer":{"pageTitle":"Science channel","content":{"pageHeaderViewModel":{
              "title":{"icon":"CHECK_CIRCLE_FILLED"},
              "image":{"avatar":{"sources":[{"url":"https://example.com/avatar"}]}},
              "banner":{"image":{"sources":[{"url":"https://example.com/banner"}]}},
              "metadata":{"contentMetadataViewModel":{"metadataRows":[
                {"metadataParts":[{"text":{"content":"@science"}}]},
                {"metadataParts":[{"text":{"content":"10K subscribers"}},{"text":{"content":"120 videos"}}]}]}}
            }}}},"metadata":{"channelMetadataRenderer":{"description":"Real description"}}}
            """, null);
        assertEquals("Science channel", profile.title);
        assertEquals("@science", profile.handle);
        assertEquals("10K subscribers", profile.subscribers);
        assertEquals("120 videos", profile.videos);
        assertEquals("Real description", profile.description);
        assertEquals("https://example.com/avatar", profile.avatarUrl);
        assertTrue(profile.verified);
    }
    @Test public void missingValuesStayMissingInsteadOfInventingZeroCounts() {
        ChannelProfile profile = ChannelProfileParser.parseJson(
                "{\"metadata\":{\"channelMetadataRenderer\":{\"title\":\"Name\"}}}", null);
        assertNull(profile.subscribers);
        assertNull(profile.bannerUrl);
        assertFalse(profile.verified);
        assertTrue(profile.links.isEmpty());
    }
    @Test public void aboutLinksDecodeRedirectsRejectUnsafeSchemesAndDeduplicate() {
        ChannelProfile profile = ChannelProfileParser.parseJson(
                "{\"metadata\":{\"channelMetadataRenderer\":{\"title\":\"Name\"}}}",
                """
                {"items":[
                 {"channelExternalLinkViewModel":{"title":{"content":"Website"},"command":{"urlEndpoint":{"url":"https://www.youtube.com/redirect?q=https%3A%2F%2Fexample.com"}}}},
                 {"channelExternalLinkViewModel":{"title":{"content":"Duplicate"},"urlEndpoint":{"url":"https://example.com"}}},
                 {"channelExternalLinkViewModel":{"title":{"content":"Bad"},"urlEndpoint":{"url":"javascript:alert(1)"}}}]}
                """);
        assertEquals(1, profile.links.size());
        assertEquals("Website", profile.links.get(0).title);
        assertEquals("https://example.com", profile.links.get(0).url);
    }
    @Test public void unrecognizedResponsesReturnNoProfile() {
        assertNull(ChannelProfileParser.parseJson("{}", null));
    }
}
