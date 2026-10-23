package com.gathin.tvhome;

/** Rules for region-specific subscription listings; no guessed provider IDs. */
final class StreamingPolicy {
    static final long FRESH_MS = 24L * 60 * 60 * 1000;
    static final long OFFLINE_MS = 7L * FRESH_MS;
    static String provider(String pkg) {
        if ("com.apple.atve.androidtv.appletv".equals(pkg)) return "Apple TV";
        if ("com.netflix.ninja".equals(pkg)) return "Netflix";
        if ("com.amazon.amazonvideo.livingroom".equals(pkg)) return "Amazon Prime Video";
        if ("in.startv.hotstar".equals(pkg)) return "JioHotstar";
        if ("com.sonyliv".equals(pkg)) return "Sony Liv";
        if ("com.graymatrix.did".equals(pkg)) return "Zee5";
        if ("com.suntv.sunnxt".equals(pkg)) return "Sun Nxt";
        return null;
    }
    static boolean fresh(long saved, long now, long limit) {
        return saved > 0 && now >= saved && now - saved < limit;
    }
    static boolean imagePath(String path) { return path != null && path.matches("/[A-Za-z0-9]+\\.jpg"); }
    /** Keep enough source pixels for the display; only subsample larger originals. */
    static int sampleSize(int width, int height, int screenWidth, int screenHeight) {
        int sample = 1;
        if (screenWidth <= 0 || screenHeight <= 0) return sample;
        while (width / (sample * 2) >= screenWidth && height / (sample * 2) >= screenHeight) sample *= 2;
        return sample;
    }
    private StreamingPolicy() { }
}
