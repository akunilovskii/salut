package com.newtube.mobile.downloads;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Downloads the original JPEG, without scaling or inheriting authenticated API interceptors. */
public final class CoverDownload {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build();
    private CoverDownload() { }

    public static void save(Context context, Video video) {
        if (video == null || !validId(video.videoId) || !ACTIVE.add(video.videoId)) return;
        String id = video.videoId;
        String name = fileName(video.title, id);
        Context app = context.getApplicationContext();
        Single.fromCallable(() -> save(app, id, name))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> ACTIVE.remove(id))
                .subscribe(complete -> MobileSnackbar.show(context, complete
                                ? R.string.mobile_cover_saved : R.string.mobile_cover_started),
                        error -> MobileSnackbar.show(context, R.string.mobile_cover_failed));
    }

    public static boolean available(Video video) { return video != null && validId(video.videoId); }

    static boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{11}"); }

    static List<String> candidates(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid video ID");
        String base = "https://i.ytimg.com/vi/" + id + "/";
        // The referenced Thumbnail Preview extension links directly to maxresdefault.jpg.
        return Arrays.asList(base + "maxresdefault.jpg", base + "sddefault.jpg", base + "hqdefault.jpg");
    }

    static String fileName(String title, String id) {
        String stem = title == null ? "cover" : title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (stem.isEmpty()) stem = "cover";
        while (stem.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 180) {
            stem = stem.substring(0, stem.offsetByCodePoints(stem.length(), -1));
        }
        return stem + " [" + id + "].jpg";
    }

    private static boolean save(Context context, String id, String name) throws IOException {
        for (String url : candidates(id)) {
            byte[] bytes;
            try (Response response = CLIENT.newCall(new Request.Builder().url(url).build()).execute()) {
                if (response.code() == 404) continue;
                if (!response.isSuccessful()) throw new IOException("Thumbnail HTTP " + response.code());
                ResponseBody body = response.body();
                if (body == null || body.contentLength() > MAX_BYTES) throw new IOException("Thumbnail size unavailable");
                try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_BYTES) throw new IOException("Thumbnail too large");
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            // YouTube sometimes returns a small placeholder with HTTP 200 for a missing size.
            if (bounds.outWidth < 320 || bounds.outHeight < 180 || !"image/jpeg".equals(bounds.outMimeType)) continue;
            if (Build.VERSION.SDK_INT < 29) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url)).setTitle(name).setMimeType("image/jpeg")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                return false;
            }
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Downloads unavailable");
            boolean committed = false;
            try {
                try (OutputStream output = context.getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IOException("Downloads is not writable");
                    output.write(bytes);
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (context.getContentResolver().update(uri, ready, null, null) != 1) throw new IOException("Cover was not published");
                committed = true;
            } finally {
                if (!committed) context.getContentResolver().delete(uri, null, null);
            }
            return true;
        }
        throw new IOException("No cover available");
    }
}
