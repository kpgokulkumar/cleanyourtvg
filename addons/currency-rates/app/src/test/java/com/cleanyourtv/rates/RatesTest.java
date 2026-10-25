package com.cleanyourtv.rates;

import org.junit.Test;
import static org.junit.Assert.*;

public class RatesTest {
    private static final String QUOTE = "{\"success\":true,\"source\":\"USD\",\"timestamp\":1792933565,\"quotes\":{\"USDINR\":100,\"USDEUR\":0.8}}";
    @Test public void convertsFromOneTimestampedUsdBase() throws Exception {
        RatesData data = new RatesData(QUOTE);
        assertEquals(100, data.usd, 0.00001);
        assertEquals(125, data.eur, 0.00001);
        String expected = java.time.Instant.ofEpochSecond(1792933565L).atZone(java.time.ZoneId.of("Asia/Kolkata"))
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm", java.util.Locale.ENGLISH));
        assertEquals(expected, data.date);
    }
    @Test public void rejectsErrorsWrongBaseAndBadNumbers() {
        for (String raw : new String[] {QUOTE.replace("true", "false"), QUOTE.replace("\"USD\"", "\"EUR\""),
            QUOTE.replace("0.8", "0"), QUOTE.replace(":100", ":-1"), QUOTE.replace("1792933565", "-1"),
            QUOTE.replace("1792933565", "999999999999"), "{}", "not json"}) {
            try { new RatesData(raw); fail("Accepted invalid quote"); }
            catch (Exception expected) { }
        }
    }
    @Test public void overlayIsRestrictedToHomeActivity() {
        assertTrue(HomeVisibility.isHome("com.gathin.tvhome", "com.gathin.tvhome.MainActivity"));
        assertFalse(HomeVisibility.isHome("com.netflix.ninja", "com.netflix.ninja.MainActivity"));
        assertFalse(HomeVisibility.isHome("com.gathin.tvhome", "android.app.Dialog"));
        assertFalse(HomeVisibility.isHome("com.android.tv.settings", "com.android.tv.settings.MainSettings"));
        assertFalse(HomeVisibility.isHome(null, null));
    }
}
