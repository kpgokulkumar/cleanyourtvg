package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlannerEntryTest {
    @Test public void trimsTitlesAndPreservesUnknownDates() {
        PlannerEntry entry = new PlannerEntry("  A film  ", null);
        assertEquals("A film", entry.title);
        assertEquals("", entry.app);
        assertEquals("", entry.date);
        assertEquals("", entry.media);
        assertEquals(0, entry.tmdbId);
    }
    @Test public void rejectsEmptyAndOverlongTitles() {
        invalid(null); invalid("  ");
        invalid(new String(new char[101]).replace('\0', 'a'));
        assertEquals(100, new PlannerEntry(new String(new char[100]).replace('\0', 'a'), "").title.length());
    }
    private void invalid(String title) {
        try { new PlannerEntry(title, ""); fail("Accepted an invalid title"); }
        catch (IllegalArgumentException expected) { }
    }
}
