package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The safe-emit boundary that {@link StreamDeobfuscator} relies on: an open marker anchored at the
 * very start of a fragment is still held back, a stray {@code >>} before the marker does not fool the
 * unclosed check, and the hold is capped at exactly the maximum hold length rather than one either
 * side of it.
 */
class TokenBoundaryTest {

    private static final int MAX_HOLD = 64;
    private static final int OPEN_LENGTH = 6;
    private static final int FILL_TO_CAP = MAX_HOLD - OPEN_LENGTH;
    private static final int PREFIX_LENGTH = 60;
    private static final int AFTER_LEADING_MARKER = 3;

    @Test
    void holdsBackAnOpenMarkerAnchoredAtTheStart() {
        assertEquals(0, TokenBoundary.of(PiiToken.OPEN + "ABC"), "an unclosed marker at index zero holds everything");
    }

    @Test
    void ignoresACloseMarkerThatPrecedesTheOpenMarker() {
        assertEquals(
            AFTER_LEADING_MARKER, TokenBoundary.of(">> " + PiiToken.OPEN + "X"),
            "the close is searched from after the open, so a leading >> leaves the marker unclosed"
        );
    }

    @Test
    void holdsExactlyUpToTheMaximumHold() {
        String value = PiiToken.OPEN + "a".repeat(FILL_TO_CAP);
        assertEquals(0, TokenBoundary.of(value), "a hold of exactly the cap is still held, not released");
    }

    @Test
    void measuresTheHoldBySubtractingTheBoundaryNotAddingIt() {
        String value = "a".repeat(PREFIX_LENGTH) + PiiToken.OPEN + "X";
        assertEquals(
            PREFIX_LENGTH, TokenBoundary.of(value),
            "a short tail past a late marker is held from the marker; the cap uses total minus boundary"
        );
    }
}
