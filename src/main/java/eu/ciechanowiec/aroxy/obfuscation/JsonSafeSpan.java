package eu.ciechanowiec.aroxy.obfuscation;

import java.util.Optional;

/**
 * Adjusts a match span so that replacing it with a backslash-free token cannot orphan a JSON escape.
 *
 * <p>The engine operates on raw JSON text. If a span started right after a dangling (odd-count)
 * backslash, the replacement would leave that backslash escaping the {@code <} of the token; if a
 * span ended with a dangling backslash, the replacement would leave it escaping the following
 * character. Either case breaks the surrounding {@code \"} or {@code \\} escape and corrupts the
 * JSON. Snapping advances the start past any leading dangling backslash and retracts the end before
 * any trailing one; if nothing is left, the span is dropped.
 */
record JsonSafeSpan(int start, int end) {

    static Optional<JsonSafeSpan> snap(CharSequence text, int rawStart, int rawEnd) {
        int start = advanceStart(text, rawStart, rawEnd);
        int end = retractEnd(text, start, rawEnd);
        if (start >= end) {
            return Optional.empty();
        }
        return Optional.of(new JsonSafeSpan(start, end));
    }

    private static int advanceStart(CharSequence text, int start, int end) {
        int cursor = start;
        while (cursor < end && isOdd(trailingBackslashes(text, cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int retractEnd(CharSequence text, int start, int end) {
        int cursor = end;
        while (cursor > start && isOdd(trailingBackslashes(text, cursor))) {
            cursor--;
        }
        return cursor;
    }

    private static boolean isOdd(int count) {
        return count % 2 != 0;
    }

    /**
     * Counts the consecutive backslashes immediately before {@code pos}.
     */
    private static int trailingBackslashes(CharSequence text, int pos) {
        int count = 0;
        int cursor = pos - 1;
        while (cursor >= 0 && text.charAt(cursor) == '\\') {
            count++;
            cursor--;
        }
        return count;
    }
}
