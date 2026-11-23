package com.newtube.mobile.feedback;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

final class FeedbackImage {
    static final int MAX_COUNT = 3;
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_PIXELS = 16_000_000;

    final byte[] data;
    final Bitmap preview;

    private FeedbackImage(byte[] data, Bitmap preview) {
        this.data = data;
        this.preview = preview;
    }

    static final class TooLarge extends IOException { }

    static FeedbackImage load(ContentResolver resolver, Uri uri) throws IOException {
        byte[] original;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (count > MAX_BYTES - buffer.size()) throw new TooLarge();
                buffer.write(chunk, 0, count);
            }
            original = buffer.toByteArray();
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(original, 0, original.length, bounds);
        boolean png = "image/png".equals(bounds.outMimeType);
        if ((!png && !"image/jpeg".equals(bounds.outMimeType)) || bounds.outWidth <= 0
                || bounds.outHeight <= 0) throw new IOException();
        if ((long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) throw new TooLarge();
        if (png) checkPng(original);
        else checkJpeg(original);
        Bitmap bitmap = BitmapFactory.decodeByteArray(original, 0, original.length);
        if (bitmap == null) throw new IOException();
        try {
            if (!png) {
                Bitmap upright = orientJpeg(bitmap, original);
                if (upright != bitmap) {
                    bitmap.recycle();
                    bitmap = upright;
                }
            }
            // Encode pixels into a fresh image, without copying source metadata.
            ByteArrayOutputStream clean = new ByteArrayOutputStream();
            if (!bitmap.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                    95, clean)) throw new IOException();
            if (clean.size() > MAX_BYTES) throw new TooLarge();
            float scale = Math.min(1f, 192f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
            Bitmap preview = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            if (preview == bitmap) return new FeedbackImage(clean.toByteArray(),
                    bitmap.copy(Bitmap.Config.ARGB_8888, false));
            return new FeedbackImage(clean.toByteArray(), preview);
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap orientJpeg(Bitmap bitmap, byte[] jpeg) throws IOException {
        ExifInterface exif = new ExifInterface(new ByteArrayInputStream(jpeg));
        Matrix transform = new Matrix();
        switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                transform.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                transform.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                transform.setScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                transform.setScale(1, -1);
                transform.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                transform.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                transform.setScale(-1, 1);
                transform.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                transform.setRotate(270);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transform, false);
    }

    private static void checkPng(byte[] data) throws IOException {
        ByteBuffer chunks = ByteBuffer.wrap(data);
        chunks.position(8);
        while (chunks.remaining() >= 12) {
            int size = chunks.getInt();
            int type = chunks.getInt();
            if (size < 0 || size > chunks.remaining() - 4 || type == 0x6163544c) {
                throw new IOException();
            }
            if (type == 0x49454e44 && size == 0 && chunks.remaining() == 4) return;
            chunks.position(chunks.position() + size + 4);
        }
        throw new IOException();
    }

    private static void checkJpeg(byte[] data) throws IOException {
        // Reject MPO (multi-picture JPEG) before Android decodes only the first frame.
        int offset = 2;
        while (offset + 3 < data.length && (data[offset++] & 255) == 255) {
            int marker = data[offset++] & 255;
            if (marker == 255) {
                offset--;
                continue;
            }
            if (marker == 0xda) return;
            int size = ((data[offset] & 255) << 8) | (data[offset + 1] & 255);
            if (size < 2 || size > data.length - offset) break;
            if (marker == 0xe2 && size >= 6 && data[offset + 2] == 'M'
                    && data[offset + 3] == 'P' && data[offset + 4] == 'F'
                    && data[offset + 5] == 0) break;
            offset += size;
        }
        throw new IOException();
    }
}
