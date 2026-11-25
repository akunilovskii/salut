# The bundled J2V8 native library looks up Java classes, constructors and callbacks.
-keep class com.eclipsesource.v8.** { *; }

# The optional Google Play provider can be absent from the embedded Cronet build.
-dontwarn com.google.android.gms.net.CronetProviderInstaller
-keep class org.chromium.net.impl.NativeCronetProvider { public <init>(android.content.Context); }

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
