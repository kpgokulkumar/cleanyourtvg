package com.gathin.tvhome;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/** Rules for region-specific subscription listings; no guessed provider IDs. */
final class StreamingPolicy {
    static final long FRESH_MS = 24L * 60 * 60 * 1000;
    static final long OFFLINE_MS = 7L * FRESH_MS;
    /** TMDB's 1280-wide backdrop: sharp on a 1080p panel at a fraction of the original's size and decode memory. */
    static final String IMAGE_SIZE = "w1280";
    static final int IMAGE_BYTES = 2_000_000;
    /** The whole artwork cache; the oldest files go first once it is larger. */
    static final long CACHE_BYTES = 24L * 1024 * 1024;

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

    /** Only ever TMDB's image host, only a checked path. */
    static String imageUrl(String path) {
        if (!imagePath(path)) throw new IllegalArgumentException();
        return "https://image.tmdb.org/t/p/" + IMAGE_SIZE + path;
    }

    /** "NETFLIX · SERIES · 2026": the service, then what it is and when, leaving out what is unknown. */
    static String eyebrow(String service, String kind, String year) {
        StringBuilder line = new StringBuilder(service == null ? "" : service.trim());
        for (String part : new String[]{kind, year}) {
            if (part == null || part.trim().isEmpty()) continue;
            if (line.length() > 0) line.append("  ·  ");
            line.append(part.trim());
        }
        return line.toString();
    }

    /** Keep enough source pixels for the display; only subsample larger originals. */
    static int sampleSize(int width, int height, int screenWidth, int screenHeight) {
        int sample = 1;
        if (screenWidth <= 0 || screenHeight <= 0) return sample;
        while (width / (sample * 2) >= screenWidth && height / (sample * 2) >= screenHeight) sample *= 2;
        return sample;
    }

    /** Deletes the oldest files until the rest fit in {@code budget} bytes. Returns how many were deleted. */
    static int prune(File[] files, long budget) {
        if (files == null) return 0;
        File[] sorted = files.clone();
        Arrays.sort(sorted, Comparator.comparingLong(File::lastModified).reversed());
        long kept = 0;
        int deleted = 0;
        for (File f : sorted) {
            if (!f.isFile()) continue;
            kept += f.length();
            if (kept > budget && f.delete()) deleted++;
        }
        return deleted;
    }

    private StreamingPolicy() { }
}
