package com.cleanyourtv.rates;

import org.junit.Test;
import static org.junit.Assert.*;
import java.math.BigDecimal;

public class GoldTest {
    private static final String PAGE = "<div id=\"zb\"><p id=\"zh\">24-Oct-2026, Sat, Evening</p>"
        + "<table><thead><tr><th>Qty</th><th>22K Gold Rate</th><th>18K Gold Rate</th><th>24K Gold Rate</th></tr></thead>"
        + "<tbody><tr><td>8g</td><td>₹ 104,560.00</td></tr>"
        + "<tr><td>1g</td><td>₹ 13,070.00</td><td>₹ 10,694.00</td><td>₹ 13,724.00</td></tr></tbody></table>";
    @Test public void calculatesEightGramsFromDated22KColumn() throws Exception {
        GoldData data = new GoldData(GoldData.parsePage(PAGE));
        assertEquals(0, new BigDecimal("104560").compareTo(data.eightGrams));
        assertEquals("2026-10-24", data.date);
    }
    @Test public void decimalRatesStayExact() throws Exception {
        GoldData data = new GoldData(GoldData.parsePage(PAGE.replace("13,070.00", "13,070.35")));
        assertEquals(0, new BigDecimal("104562.80").compareTo(data.eightGrams));
    }
    @Test public void changedColumnsMissingDateAndBlockedPagesAreRejected() {
        for (String bad : new String[] {PAGE.replace("22K", "24K"), PAGE.replace("24-Oct-2026", "30-Feb-2026"),
                PAGE.replace("1g", "10g"), PAGE.replace("13,070.00", "0.00"), "Access denied"}) {
            try { GoldData.parsePage(bad); fail("Accepted malformed quote"); }
            catch (Exception expected) { }
        }
    }
    @Test public void cannotUseAnotherCityOrPurity() {
        for (String bad : new String[] {"{\"city\":\"Mumbai\",\"karat\":22,\"perGram\":\"10000\",\"date\":\"2026-10-24\"}",
                "{\"city\":\"Chennai\",\"karat\":24,\"perGram\":\"10000\",\"date\":\"2026-10-24\"}"}) {
            try { new GoldData(bad); fail("Accepted wrong market"); }
            catch (Exception expected) { }
        }
    }
}
