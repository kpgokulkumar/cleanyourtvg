package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class StreamingPolicyTest {
    @Test public void dockProvidersUseCurrentNamesAndUnsupportedAppsHaveNoMovie() {
        assertEquals("Apple TV", StreamingPolicy.provider("com.apple.atve.androidtv.appletv"));
        assertNull(StreamingPolicy.provider("com.apple.atve.androidtv.store"));
        assertEquals("Netflix", StreamingPolicy.provider("com.netflix.ninja"));
        assertEquals("Amazon Prime Video", StreamingPolicy.provider("com.amazon.amazonvideo.livingroom"));
        assertEquals("JioHotstar", StreamingPolicy.provider("in.startv.hotstar"));
        assertNull(StreamingPolicy.provider("com.google.android.youtube.tv"));
        assertNull(StreamingPolicy.provider("org.videolan.vlc"));
    }
    @Test public void originalsKeepAtLeastTheScreenResolution() {
        assertEquals(1, StreamingPolicy.sampleSize(1920, 1080, 1920, 1080));
        assertEquals(2, StreamingPolicy.sampleSize(3840, 2160, 1920, 1080));
        assertEquals(4, StreamingPolicy.sampleSize(7680, 4320, 1920, 1080));
        assertEquals(1, StreamingPolicy.sampleSize(1600, 1280, 1920, 1080));
        assertEquals(1, StreamingPolicy.sampleSize(3000, 1688, 1920, 1080));
        assertEquals(1, StreamingPolicy.sampleSize(1920, 1080, 0, 0));
    }
    @Test public void clockRollbackAndExpiredAvailabilityCannotBeFresh() {
        long saved = 1_000_000;
        assertTrue(StreamingPolicy.fresh(saved, saved + 1000, StreamingPolicy.FRESH_MS));
        assertFalse(StreamingPolicy.fresh(saved, saved - 1, StreamingPolicy.FRESH_MS));
        assertFalse(StreamingPolicy.fresh(saved, saved + StreamingPolicy.FRESH_MS, StreamingPolicy.FRESH_MS));
        assertFalse(StreamingPolicy.fresh(saved, saved + StreamingPolicy.OFFLINE_MS, StreamingPolicy.OFFLINE_MS));
    }
    @Test public void backdropPathsCannotEscapeTheTrustedImageHost() {
        assertTrue(StreamingPolicy.imagePath("/abc123.jpg"));
        assertFalse(StreamingPolicy.imagePath("//example.com/art.jpg"));
        assertFalse(StreamingPolicy.imagePath("/../art.jpg"));
        assertFalse(StreamingPolicy.imagePath("null"));
    }
}
