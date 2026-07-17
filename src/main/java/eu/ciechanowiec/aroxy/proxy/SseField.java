package eu.ciechanowiec.aroxy.proxy;

import java.util.Optional;

/**
 * Locates the raw, still-JSON-escaped string value of a streamed delta field inside one SSE event
 * block: the value after {@code "text":"} (a {@code text_delta}) or {@code "partial_json":"} (an
 * {@code input_json_delta}), up to its closing unescaped double-quote. The span is exposed as a
 * half-open {@code [start, end)} interval so the caller can substitute a reversed value back in
 * place while leaving the surrounding framing untouched.
 *
 * @param start the index just after the opening quote of the value
 * @param end   the index of the closing unescaped quote of the value
 */
record SseField(int start, int end) {

    private static final String TEXT_KEY = "\"text\":\"";
    private static final String JSON_KEY = "\"partial_json\":\"";

    static Optional<SseField> locate(String block) {
        return valueStart(block).flatMap(start -> spanFrom(block, start));
    }

    String value(String block) {
        return block.substring(this.start, this.end);
    }

    private static Optional<SseField> spanFrom(CharSequence block, int start) {
        return closingQuote(block, start).map(end -> new SseField(start, end));
    }

    private static Optional<Integer> valueStart(String block) {
        int text = block.indexOf(TEXT_KEY);
        if (text >= 0) {
            return Optional.of(text + TEXT_KEY.length());
        }
        int json = block.indexOf(JSON_KEY);
        return json >= 0 ? Optional.of(json + JSON_KEY.length()) : Optional.empty();
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
