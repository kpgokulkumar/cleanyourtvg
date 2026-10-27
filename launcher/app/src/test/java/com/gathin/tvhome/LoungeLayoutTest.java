package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class LoungeLayoutTest {
    @Test public void columnsTouchBothEdgesWithEqualGaps() {
        // Five 46 px slots over 272 px: the first starts at 0, the last ends at 272.
        assertEquals(23f, LoungeLayout.centre(0, 5, 272, 46), 0.001f);
        assertEquals(249f, LoungeLayout.centre(4, 5, 272, 46), 0.001f);
        float gap = LoungeLayout.centre(1, 5, 272, 46) - LoungeLayout.centre(0, 5, 272, 46);
        assertEquals(gap, LoungeLayout.centre(4, 5, 272, 46) - LoungeLayout.centre(3, 5, 272, 46), 0.001f);
    }

    @Test public void aSingleButtonSitsAtTheRightEdge() {
        assertEquals(249f, LoungeLayout.centre(0, 1, 272, 46), 0.001f);
    }

    @Test public void knownDockAppsHaveAMarkAndOthersUseTheirIcon() {
        assertNotNull(BrandMarks.of("com.netflix.ninja"));
        assertEquals("Prime Video", BrandMarks.of("com.amazon.amazonvideo.livingroom").name);
        assertNull(BrandMarks.of("org.videolan.vlc"));
        assertNull(BrandMarks.of(null));
    }
}
