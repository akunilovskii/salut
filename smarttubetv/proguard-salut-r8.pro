# Measure code and resource shrinking without R8's bytecode optimizations.
-dontoptimize

# The bundled J2V8 native library looks up Java classes, constructors and callbacks.
-keep class com.eclipsesource.v8.** { *; }

# The optional Google Play provider can be absent from the embedded Cronet build.
-dontwarn com.google.android.gms.net.CronetProviderInstaller
# The media transport uses the embedded engine and its generated JNI bindings.
-keep class org.chromium.net.impl.** { *; }
-keep class internal.org.jni_zero.** { *; }
-keep class org.jni_zero.** { *; }

# WebView calls the playback token bridge through methods named in bundled JavaScript.
-keepclassmembers class com.liskovsoft.youtubeapi.app.potokennp2.generators.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Response readers construct playback formats and their nested models reflectively.
-keep class com.liskovsoft.youtubeapi.videoinfo.models.** { *; }

# Media3 resolves optional source factories by class name. The HTTP fallback
# constructs the Brotli decoder through its public InputStream constructor.
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { public <init>(...); }
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { public <init>(...); }
-keep class org.brotli.dec.BrotliInputStream { public <init>(java.io.InputStream); }

# HapticSwipeRefreshLayout reads these fields by name.
-keepclassmembers class androidx.swiperefreshlayout.widget.SwipeRefreshLayout {
    float mTotalUnconsumed;
    float mTotalDragDistance;
    boolean mIsBeingDragged;
    float mInitialMotionY;
    int mActivePointerId;
}

# LiveDashManifestParser reads and updates Media3 manifest fields by name.
-keepclassmembers class androidx.media3.exoplayer.dash.manifest.DashManifest {
    long publishTimeMs;
}
-keepclassmembers class androidx.media3.exoplayer.dash.manifest.Period {
    long startMs;
}
-keepclassmembers class androidx.media3.exoplayer.dash.manifest.Representation {
    *** segmentBase;
}
-keepclassmembers class androidx.media3.exoplayer.dash.manifest.SegmentBase {
    long presentationTimeOffset;
}
-keepclassmembers class androidx.media3.exoplayer.dash.manifest.SegmentBase$MultiSegmentBase {
    long startNumber;
    java.util.List segmentTimeline;
}
-keepclassmembers class androidx.media3.exoplayer.dash.manifest.SegmentBase$SegmentList {
    java.util.List mediaSegments;
}
-keepclassmembers class androidx.media3.exoplayer.dash.manifest.SegmentBase$SegmentTimelineElement {
    long startTime;
    long duration;
}

# The response adapters construct models and populate annotated fields reflectively.
-keepclasseswithmembers class com.liskovsoft.** {
    @com.liskovsoft.googlecommon.common.converters.jsonpath.JsonPath <fields>;
}
-keepclasseswithmembers class com.liskovsoft.** {
    @com.liskovsoft.googlecommon.common.converters.regexp.RegExp <fields>;
}
-keepclassmembers class com.liskovsoft.** {
    public <init>();
}

# Gson uses field names and generic signatures to populate response objects.
# Keep model graphs, including nested DTOs and unannotated Kotlin fields.
-keep class com.liskovsoft.youtubeapi.**.gen.** { *; }
-keep class com.liskovsoft.googlecommon.common.models.gen.** { *; }
-keep class com.liskovsoft.youtubeapi.innertube.models.** { *; }
-keep class com.liskovsoft.youtubeapi.dearrow.data.** { *; }
-keep class com.liskovsoft.youtubeapi.app.potokennp2.visitor.data.** { *; }
-keep class com.liskovsoft.youtubeapi.app.potokencloud.PoToken*Response* { *; }
-keep class com.liskovsoft.youtubeapi.app.potokencloud2.PoTokenResponse { *; }
-keep class com.liskovsoft.googleapi.*.data.** { *; }
-keep class com.liskovsoft.googlecommon.common.constants.data.** { *; }
-keep class com.liskovsoft.youtubeapi.app.nsigsolver.runtime.SolverOutput { *; }
-keep class com.liskovsoft.youtubeapi.app.nsigsolver.runtime.ResponseData { *; }
-keep class com.liskovsoft.youtubeapi.app.nsigsolver.runtime.SolverMemo$CheckOutput { *; }

# dnsjava ships optional desktop resolvers and compile-time annotations.
-dontwarn com.sun.jna.Library
-dontwarn com.sun.jna.Memory
-dontwarn com.sun.jna.Native
-dontwarn com.sun.jna.Pointer
-dontwarn com.sun.jna.Structure
-dontwarn com.sun.jna.Structure$FieldOrder
-dontwarn com.sun.jna.Structure$ByReference
-dontwarn com.sun.jna.WString
-dontwarn com.sun.jna.platform.win32.Win32Exception
-dontwarn com.sun.jna.ptr.IntByReference
-dontwarn com.sun.jna.win32.W32APIOptions
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.DirContext
-dontwarn javax.naming.directory.InitialDirContext
-dontwarn lombok.Generated
