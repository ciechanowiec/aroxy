package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The two pure readers the SSE path is built on: finding the blank line that ends an event, and
 * finding the span of one JSON string value inside it. Both walk indexes by hand, so their edges are
 * where an off-by-one hides: a delimiter that finishes exactly at the end of the buffer, a key at
 * offset zero, and a value whose own backslashes decide which quote closes it.
 */
class SseFramingTest {

    private static final String TEXT_KEY = "\"text\":\"";

    @Test
    void findsTheBlankLineInEitherFraming() {
        assertEquals(
            Optional.of(new Delimiter(4, 2)), DoubleNewline.find(bytes("data\n\nnext"), 0),
            "a bare newline pair ends the event where the first newline sits"
        );
        assertEquals(
            Optional.of(new Delimiter(4, 4)), DoubleNewline.find(bytes("data\r\n\r\nnext"), 0),
            "the rewritten framing spans four bytes"
        );
    }

    // The delimiter that finishes exactly at the end of the buffer is the one a reader is most likely
    // to skip, and skipping it means the last event of a stream never drains.
    @Test
    void findsAFramingThatEndsExactlyAtTheBufferEnd() {
        assertEquals(
            Optional.of(new Delimiter(0, 4)), DoubleNewline.find(bytes("\r\n\r\n"), 0),
            "a buffer that is nothing but the delimiter still contains it"
        );
        assertEquals(
            Optional.of(new Delimiter(4, 4)), DoubleNewline.find(bytes("data\r\n\r\n"), 0),
            "the delimiter is found when it runs to the last byte"
        );
    }

    @Test
    void reportsNoDelimiterRatherThanReadingPastTheBuffer() {
        assertTrue(DoubleNewline.find(bytes("data\n"), 0).isEmpty(), "one newline is not a blank line");
        assertTrue(DoubleNewline.find(bytes("data\r\n"), 0).isEmpty(), "one CRLF is not a blank line");
        assertTrue(DoubleNewline.find(bytes(""), 0).isEmpty(), "an empty buffer has no delimiter");
        assertTrue(DoubleNewline.find(bytes("no break here"), 0).isEmpty(), "ordinary text has none");
    }

    @Test
    void searchesOnlyFromTheGivenOffset() {
        assertEquals(
            Optional.of(new Delimiter(8, 2)), DoubleNewline.find(bytes("one\n\ntwo\n\n"), 5),
            "the first delimiter is behind the offset, so the second one is found"
        );
    }

    // The key can sit at the very start of the block, which is the index an "is it present" test
    // written as a positive check quietly excludes.
    @Test
    void locatesAValueWhoseKeyStartsAtTheFirstCharacter() {
        String block = TEXT_KEY + "value\"";
        assertEquals(
            "value", SseField.locate(block, TEXT_KEY).orElseThrow().value(block),
            "a key at offset zero is still present"
        );
    }

    // Which quote closes the value is decided by the parity of the backslashes before it. An escaped
    // quote does not close it, and an escaped backslash is not an escape, so the quote after it does.
    @Test
    void endsTheValueAtTheFirstQuoteThatIsNotEscaped() {
        String escapedQuote = "\"text\":\"a\\\"b\" tail";
        assertEquals(
            "a\\\"b", SseField.locate(escapedQuote, TEXT_KEY).orElseThrow().value(escapedQuote),
            "an escaped quote is part of the value"
        );
        String escapedBackslash = "\"text\":\"a\\\\\" tail";
        assertEquals(
            "a\\\\", SseField.locate(escapedBackslash, TEXT_KEY).orElseThrow().value(escapedBackslash),
            "two backslashes escape each other, so the quote after them closes the value"
        );
        String leadingEscapedQuote = "\"text\":\"\\\"x\" tail";
        assertEquals(
            "\\\"x", SseField.locate(leadingEscapedQuote, TEXT_KEY).orElseThrow().value(leadingEscapedQuote),
            "a value beginning with an escaped quote is not closed by it"
        );
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
