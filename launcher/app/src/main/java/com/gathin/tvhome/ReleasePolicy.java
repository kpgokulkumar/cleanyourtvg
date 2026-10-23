package com.gathin.tvhome;

import java.time.LocalDate;

/** Only a country-specific digital date can place an imported title on the India board. */
final class ReleasePolicy {
    static String digitalDate(String country, int type, String timestamp) {
        if (!"IN".equals(country) || type != 4 || timestamp == null || timestamp.length() < 10) return "";
        try { return LocalDate.parse(timestamp.substring(0, 10)).toString(); }
        catch (RuntimeException e) { return ""; }
    }
    static String choose(String saved, String candidate, String today) {
        if (candidate.isEmpty()) return saved;
        if (saved.isEmpty()) return candidate;
        boolean future = candidate.compareTo(today) >= 0, previousFuture = saved.compareTo(today) >= 0;
        if (future != previousFuture) return future ? candidate : saved;
        return (future ? candidate.compareTo(saved) < 0 : candidate.compareTo(saved) > 0) ? candidate : saved;
    }
}
