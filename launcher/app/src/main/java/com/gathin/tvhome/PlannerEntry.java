package com.gathin.tvhome;

/** A saved release; unknown dates stay empty and no clock time is inferred. */
final class PlannerEntry {
    final String title, app;
    String date = "", media = "";
    int tmdbId;
    PlannerEntry(String title, String app) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 100)
            throw new IllegalArgumentException();
        this.title = title.trim();
        this.app = app == null ? "" : app;
    }
}
