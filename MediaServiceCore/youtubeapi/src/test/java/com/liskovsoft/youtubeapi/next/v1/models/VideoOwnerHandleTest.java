package com.liskovsoft.youtubeapi.next.v1.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;

import com.liskovsoft.googlecommon.common.converters.jsonpath.converter.JsonPathConverterFactory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.annotation.Annotation;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Converter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class VideoOwnerHandleTest {
    @Test public void canonicalHandleRemainsSeparateFromTheDisplayName() throws Exception {
        VideoOwner owner = parse("{\"title\":{\"simpleText\":\"Display name\"},"
                + "\"navigationEndpoint\":{\"browseEndpoint\":{\"canonicalBaseUrl\":\"/@realhandle\"}}}");
        assertEquals("@realhandle", owner.getChannelHandle());
        assertEquals("Display name", owner.getVideoAuthor());
    }

    @Test public void channelIdsAndMissingUrlsNeverBecomeInventedHandles() throws Exception {
        assertNull(parse("{\"title\":{\"simpleText\":\"Display name\"}}").getChannelHandle());
        assertNull(parse("{\"navigationEndpoint\":{\"browseEndpoint\":{"
                + "\"canonicalBaseUrl\":\"/channel/UCexample\"}}}").getChannelHandle());
    }

    private static VideoOwner parse(String json) throws Exception {
        Converter<ResponseBody, ?> converter = JsonPathConverterFactory.create()
                .responseBodyConverter(VideoOwner.class, new Annotation[0], null);
        return (VideoOwner) converter.convert(ResponseBody.create(MediaType.get("application/json"), json));
    }
}
