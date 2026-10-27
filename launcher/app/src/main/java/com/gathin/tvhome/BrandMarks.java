package com.gathin.tvhome;

/**
 * The lounge dock's marks: a one-colour symbol per streaming app, drawn white at rest and in the brand's own
 * colour on the focused white disc. An app without a mark here shows its own icon instead (LoungeHome).
 * Symbols: Simple Icons 11.15.0 (CC0); the Prime and Hotstar ones are simplified. The marks belong to their owners.
 */
final class BrandMarks {
    static final class Mark {
        final int glyph;
        /** The symbol's colour on the focused disc. */
        final int color;
        final String name;

        Mark(int glyph, int color, String name) {
            this.glyph = glyph;
            this.color = color;
            this.name = name;
        }
    }

    /** Dark ink for marks whose brand colour is black or white. */
    static final int INK = 0xFF0B0B0F;

    static Mark of(String pkg) {
        if (pkg == null) return null;
        switch (pkg) {
            case "com.google.android.youtube.tv": return new Mark(R.drawable.dock_youtube, 0xFFFF0033, "YouTube");
            case "com.netflix.ninja": return new Mark(R.drawable.dock_netflix, 0xFFE50914, "Netflix");
            case "com.amazon.amazonvideo.livingroom": return new Mark(R.drawable.dock_amazonprime, 0xFF1A98FF, "Prime Video");
            case "in.startv.hotstar": return new Mark(R.drawable.dock_hotstar, 0xFF1F4FD8, "JioHotstar");
            case "com.apple.atve.androidtv.appletv": return new Mark(R.drawable.dock_appletv, INK, "Apple TV");
            default: return null;
        }
    }

    private BrandMarks() { }
}
