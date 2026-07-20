package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.FragmentRule;
import java.util.Optional;

/**
 * Locates the raw, still-JSON-escaped string value introduced by a given key inside one SSE event
 * block, up to its closing unescaped double-quote. The span is exposed as a half-open
 * {@code [start, end)} interval so the caller can substitute a reversed value back in place while
 * leaving the surrounding framing untouched.
 *
 * <p>The key is supplied by the matching {@link FragmentRule} rather than guessed, so a block shape
 * whose text sits under an unusual key is read correctly instead of being missed.
 *
 * @param start the index just after the opening quote of the value
 * @param end   the index of the closing unescaped quote of the value
 */
record SseField(int start, int end) {

    static Optional<SseField> locate(String block, String fieldKey) {
        return valueStart(block, fieldKey).flatMap(start -> spanFrom(block, start));
    }

    String value(String block) {
        return block.substring(this.start, this.end);
    }

    private static Optional<SseField> spanFrom(CharSequence block, int start) {
        return closingQuote(block, start).map(end -> new SseField(start, end));
    }

    private static Optional<Integer> valueStart(String block, String fieldKey) {
        int at = block.indexOf(fieldKey);
        return at >= 0 ? Optional.of(at + fieldKey.length()) : Optional.empty();
    }

    private static Optional<Integer> closingQuote(CharSequence block, int from) {
        int length = block.length();
        for (int index = from; index < length; index++) {
            if (isUnescapedQuote(block, from, index)) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    private static boolean isUnescapedQuote(CharSequence block, int from, int index) {
        return block.charAt(index) == '"' && backslashesBefore(block, from, index) % 2 == 0;
    }

    private static int backslashesBefore(CharSequence block, int from, int index) {
        int count = 0;
        int cursor = index - 1;
        while (cursor >= from && block.charAt(cursor) == '\\') {
            count++;
            cursor--;
        }
        return count;
    }
}
