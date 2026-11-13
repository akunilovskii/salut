package com.newtube.mobile.audio;

import com.newtube.mobile.ui.common.MobileSnackbar;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SwitchAudioOutput implements TeeAudioProcessor.AudioBufferSink {
    private static SwitchAudioOutput instance;
    public static synchronized SwitchAudioOutput get(Context context) {
        if (instance == null) {
            Context application = context.getApplicationContext();
            instance = new SwitchAudioOutput(application, (connected, failed) -> {
                if (failed && instance != null && !instance.isRetrying() && instance.isSelected()) {
                    MobileSnackbar.show(application, application.getString(com.liskovsoft.smartyoutubetv2.tv.R.string.switch_audio_failed), null, null, 3500);
                }
            });
        }
        return instance;
    }
    public void select(boolean selected) {
        preferences.edit().putBoolean(SELECTED, selected).apply();
        if (selected) connect(); else disconnect();
    }
    public interface Listener {
        void onConnectionChanged(boolean connected, boolean failed);
    }

    private static final String TAG = "SwitchAudioOutput";
    private static final String PREFS = "switch_audio_output";
    private static final String VOLUME = "volume";
    private static final String CONNECTED = "connected";
    private static final String SELECTED = "selected";
    static final String SHOW_BUTTON = "show_button";
    private static final String ADDRESS = "address";
    private static final int DEFAULT_VOLUME = 100;
    private static final int FRAME_BYTES = 3840;
    private static final int SAMPLE_RATE = 48000;
    private static final int MAX_CONNECT_RETRIES = 3;
    private static final long MAX_QUEUED_BYTES = FRAME_BYTES * 25L;

    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService discoveryExecutor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS).build();
    private final Listener listener;
    private final byte[] frame = new byte[FRAME_BYTES];

    private volatile WebSocket socket;
    private volatile boolean connected;
    private volatile boolean connecting;
    private volatile int volume;
    private volatile int generation;
    private volatile int retryAttempts;
    private volatile boolean retrying;
    private final Runnable retryConnection = () -> {
        if (isSelected() && !connected && !connecting) {
            startConnect();
        }
    };
    private int frameLength;
    private int inputRate;
    private int inputChannels;
    private int inputEncoding;
    private long inputFrameCount;
    private double nextOutputPosition;
    private float previousLeft;
    private float previousRight;
    private boolean hasPrevious;
    private long sentFrames;

    public SwitchAudioOutput(Context context, Listener listener) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putBoolean(CONNECTED, false).apply();
        volume = preferences.getInt(VOLUME, DEFAULT_VOLUME);
        this.listener = listener;
    }

    public static boolean isButtonShown(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SHOW_BUTTON, true);
    }

    public static void setButtonShown(Context context, boolean shown) {
        if (!shown) {
            if (instance != null) instance.select(false);
            else setSelected(context, false);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SHOW_BUTTON, shown).apply();
    }

    public static int getSavedVolume(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(VOLUME, DEFAULT_VOLUME);
    }

    public static void saveVolume(Context context, int value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(VOLUME, Math.max(0, Math.min(100, value))).apply();
    }

    public static void setSelected(Context context, boolean selected) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SELECTED, selected).apply();
    }

    public static boolean isSelected(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SELECTED, false);
    }

    public boolean isSelected() {
        return preferences.getBoolean(SELECTED, false);
    }

    public boolean isConnecting() {
        return connecting;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isRetrying() {
        return retrying;
    }

    public void setVolume(int value) {
        volume = Math.max(0, Math.min(100, value));
        preferences.edit().putInt(VOLUME, volume).apply();
    }

    public void connect() {
        retryAttempts = 0;
        retrying = false;
        mainHandler.removeCallbacks(retryConnection);
        startConnect();
    }

    private void startConnect() {
        int requestGeneration = ++generation;
        connecting = true;
        WebSocket oldSocket = socket;
        if (oldSocket != null) {
            oldSocket.close(1000, null);
        }
        socket = null;
        connected = false;
        resetPendingAudio();
        preferences.edit().putBoolean(CONNECTED, false).apply();
        discoveryExecutor.execute(() -> {
            try {
                String address = discoverAddress(requestGeneration);
                if (generation != requestGeneration) {
                    return;
                }
                Request request = new Request.Builder()
                        .url("ws://" + address + ":12889/audio").build();
                socket = client.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        if (generation != requestGeneration) {
                            webSocket.close(1000, null);
                            return;
                        }
                        socket = webSocket;
                        connecting = false;
                        connected = true;
                        sentFrames = 0;
                        Log.i(TAG, "Connected to Switch " + address);
                        retryAttempts = 0;
                        retrying = false;
                        mainHandler.removeCallbacks(retryConnection);
                        preferences.edit().putString(ADDRESS, address).putBoolean(CONNECTED, true).apply();
                        mainHandler.post(() -> listener.onConnectionChanged(true, false));
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                        Log.e(TAG, "Switch audio connection failed", error);
                        fail(requestGeneration);
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        fail(requestGeneration);
                    }
                });
            } catch (IOException error) {
                Log.e(TAG, "Switch audio discovery failed", error);
                fail(requestGeneration);
            }
        });
    }

    public void disconnect() {
        generation++;
        mainHandler.removeCallbacks(retryConnection);
        retryAttempts = 0;
        retrying = false;
        connecting = false;
        connected = false;
        preferences.edit().putBoolean(CONNECTED, false).apply();
        WebSocket oldSocket = socket;
        socket = null;
        resetPendingAudio();
        if (oldSocket != null) {
            oldSocket.close(1000, null);
        }
        mainHandler.post(() -> listener.onConnectionChanged(false, false));
    }

    public void release() {
        disconnect();
        discoveryExecutor.shutdownNow();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private void fail(int requestGeneration) {
        if (generation != requestGeneration) {
            return;
        }
        generation++;
        socket = null;
        connecting = false;
        connected = false;
        resetPendingAudio();
        preferences.edit().putBoolean(CONNECTED, false).apply();
        if (isSelected() && retryAttempts < MAX_CONNECT_RETRIES) {
            retryAttempts++;
            retrying = true;
            mainHandler.postDelayed(retryConnection, 2000L * retryAttempts);
        } else {
            retrying = false;
        }
        mainHandler.post(() -> listener.onConnectionChanged(false, true));
    }

    private synchronized void resetPendingAudio() {
        frameLength = 0;
        inputFrameCount = 0;
        nextOutputPosition = 0;
        hasPrevious = false;
    }

    private String discoverAddress(int requestGeneration) throws IOException {
        try (DatagramSocket datagramSocket = new DatagramSocket()) {
            datagramSocket.setBroadcast(true);
            datagramSocket.setSoTimeout(600);
            byte[] request = "DISCOVER_AUDIOCAST".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            String savedAddress = preferences.getString(ADDRESS, "192.168.0.178");
            if (savedAddress != null) {
                sendDiscovery(datagramSocket, request, savedAddress);
            }
            sendDiscovery(datagramSocket, request, "255.255.255.255");
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && generation == requestGeneration) {
                byte[] data = new byte[512];
                DatagramPacket response = new DatagramPacket(data, data.length);
                try {
                    datagramSocket.receive(response);
                } catch (java.net.SocketTimeoutException timeout) {
                    if (savedAddress != null) {
                        sendDiscovery(datagramSocket, request, savedAddress);
                    }
                    sendDiscovery(datagramSocket, request, "255.255.255.255");
                    continue;
                }
                try {
                    JSONObject details = new JSONObject(new String(response.getData(), 0, response.getLength(),
                            java.nio.charset.StandardCharsets.UTF_8));
                    if ("Switch Podcast".equals(details.optString("server_name"))
                            && details.optInt("port") == 12889) {
                        return response.getAddress().getHostAddress();
                    }
                } catch (JSONException ignored) {
                }
            }
        }
        throw new IOException("Switch Podcast receiver was not found");
    }

    private void sendDiscovery(DatagramSocket socket, byte[] request, String address) throws IOException {
        DatagramPacket packet = new DatagramPacket(request, request.length,
                InetAddress.getByName(address), 12888);
        socket.send(packet);
    }

    @Override
    public synchronized void flush(int sampleRateHz, int channelCount, int encoding) {
        inputRate = sampleRateHz;
        inputChannels = channelCount;
        inputEncoding = encoding;
        inputFrameCount = 0;
        nextOutputPosition = 0;
        hasPrevious = false;
        frameLength = 0;
    }

    @Override
    public synchronized void handleBuffer(ByteBuffer buffer) {
        if (!connected || inputRate <= 0 || inputChannels <= 0
                || (inputEncoding != C.ENCODING_PCM_16BIT && inputEncoding != C.ENCODING_PCM_FLOAT)) {
            return;
        }
        ByteBuffer input = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int bytesPerSample = inputEncoding == C.ENCODING_PCM_16BIT ? 2 : 4;
        while (input.remaining() >= inputChannels * bytesPerSample) {
            float left = readSample(input);
            float right = inputChannels > 1 ? readSample(input) : left;
            for (int channel = 2; channel < inputChannels; channel++) {
                readSample(input);
            }
            if (!hasPrevious) {
                writeStereo(left, right);
                nextOutputPosition = (double) inputRate / SAMPLE_RATE;
                hasPrevious = true;
            } else {
                while (nextOutputPosition <= inputFrameCount + 0.000001) {
                    float fraction = (float) (nextOutputPosition - (inputFrameCount - 1));
                    writeStereo(previousLeft + (left - previousLeft) * fraction,
                            previousRight + (right - previousRight) * fraction);
                    nextOutputPosition += (double) inputRate / SAMPLE_RATE;
                }
            }
            previousLeft = left;
            previousRight = right;
            inputFrameCount++;
        }
    }

    private float readSample(ByteBuffer input) {
        if (inputEncoding == C.ENCODING_PCM_FLOAT) {
            return input.getFloat();
        }
        return input.getShort() / 32768f;
    }

    private void writeStereo(float left, float right) {
        writeSample(left);
        writeSample(right);
        if (frameLength == FRAME_BYTES) {
            WebSocket activeSocket = socket;
            if (activeSocket != null && activeSocket.queueSize() < MAX_QUEUED_BYTES) {
                if (activeSocket.send(ByteString.of(frame, 0, FRAME_BYTES)) && ++sentFrames % 250 == 0) {
                    Log.i(TAG, "Audio frames=" + sentFrames + " volume=" + volume);
                }
            }
            frameLength = 0;
        }
    }

    private void writeSample(float value) {
        float level = volume / 100f;
        float scaled = value * level * level;
        // Raise quiet source audio at full volume without clipping loud peaks.
        float limited = 3f * scaled / (1f + 2f * Math.abs(scaled));
        int sample = Math.round(Math.max(-1f, Math.min(1f, limited)) * 32767f);
        frame[frameLength++] = (byte) sample;
        frame[frameLength++] = (byte) (sample >> 8);
    }
}
