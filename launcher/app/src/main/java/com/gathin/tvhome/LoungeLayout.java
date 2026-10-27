package com.gathin.tvhome;

/** Column maths for LoungeHome, kept apart so it can be tested on the JVM. */
final class LoungeLayout {
    /**
     * Centre of column {@code column} of {@code columns} spread over {@code width}: the first slot touches the left
     * edge and the last the right, with equal gaps between. {@code slot} is the widest button, so a row of smaller
     * buttons still lines up on the same centres as the row of larger ones.
     */
    static float centre(int column, int columns, float width, float slot) {
        if (columns <= 1) return width - slot / 2f;
        return slot / 2f + column * (width - slot) / (columns - 1);
    }

    private LoungeLayout() { }
}
