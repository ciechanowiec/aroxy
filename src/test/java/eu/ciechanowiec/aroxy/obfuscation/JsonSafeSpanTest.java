package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Snapping a match span around dangling JSON backslashes: a leading dangling backslash advances the
 * start, a trailing one retracts the end, and a span that collapses to nothing is dropped so the
 * replacement can never orphan an escape.
 */
class JsonSafeSpanTest {

    @Test
    void keepsACleanSpanUnchanged() {
        Optional<JsonSafeSpan> snapped = JsonSafeSpan.snap("abcXYdef", 3, 5);
        JsonSafeSpan span = snapped.orElseThrow();
        assertEquals(3, span.start(), "a span with no dangling backslash keeps its start");
        assertEquals(5, span.end(), "a span with no dangling backslash keeps its end");
    }

    @Test
    void advancesTheStartPastALeadingDanglingBackslash() {
        Optional<JsonSafeSpan> snapped = JsonSafeSpan.snap("a\\Xb", 2, 4);
        assertEquals(
            3, snapped.orElseThrow().start(), "the start advances past the char the dangling backslash escapes"
        );
    }

    @Test
    void retractsTheEndBeforeATrailingDanglingBackslash() {
        Optional<JsonSafeSpan> snapped = JsonSafeSpan.snap("X\\", 0, 2);
        assertEquals(1, snapped.orElseThrow().end(), "the end retracts before the trailing dangling backslash");
    }

    @Test
    void dropsASpanThatCollapsesToNothing() {
        assertTrue(JsonSafeSpan.snap("\\X", 1, 2).isEmpty(), "a span left empty after snapping is dropped");
    }

    @Test
    void anEvenBackslashRunIsNotDangling() {
        Optional<JsonSafeSpan> snapped = JsonSafeSpan.snap("\\\\X", 2, 3);
        assertEquals(2, snapped.orElseThrow().start(), "a paired backslash run does not orphan the token");
    }
}
