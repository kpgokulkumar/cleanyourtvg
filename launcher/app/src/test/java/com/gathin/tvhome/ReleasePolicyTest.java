package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReleasePolicyTest {
    @Test public void onlyIndiaDigitalDatesQualify() {
        assertEquals("2026-10-23", ReleasePolicy.digitalDate("IN", 4, "2026-10-23T00:00:00.000Z"));
        assertEquals("", ReleasePolicy.digitalDate("US", 4, "2026-10-23T00:00:00Z"));
        assertEquals("", ReleasePolicy.digitalDate("IN", 3, "2026-10-23"));
        assertEquals("", ReleasePolicy.digitalDate("IN", 6, "2026-10-23"));
        assertEquals("", ReleasePolicy.digitalDate("IN", 4, "2026-02-30"));
        assertEquals("", ReleasePolicy.digitalDate("IN", 4, null));
    }
    @Test public void prefersNextDigitalDateThenMostRecentPastDate() {
        String today = "2026-10-23";
        assertEquals("2026-10-24", ReleasePolicy.choose("2026-10-22", "2026-10-24", today));
        assertEquals("2026-10-24", ReleasePolicy.choose("2026-10-24", "2026-10-22", today));
        assertEquals("2026-10-24", ReleasePolicy.choose("2026-10-27", "2026-10-24", today));
        assertEquals("2026-10-22", ReleasePolicy.choose("2026-10-20", "2026-10-22", today));
        assertEquals("2026-10-23", ReleasePolicy.choose("2026-10-24", today, today));
        assertEquals("2026-10-24", ReleasePolicy.choose("2026-10-24", "", today));
    }
}
