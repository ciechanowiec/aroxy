package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Trimming a later rule's match around a token an earlier rule already minted. The token occupies the
 * half-open range {@code [4, 15]} in the fixture text. A match is trimmed to its largest token-free
 * sub-range, dropped when it lies inside or still straddles the token, and kept verbatim when it is
 * clear of the token entirely.
 */
class TokenRegionsTest {

    private static final String TEXT = "aaaa<<PII:X:1>>bbbb";
    private static final int TOKEN_END = 15;
    private static final int AFTER_TOKEN_START = 16;
    private static final int AFTER_TOKEN_END = 18;
    private static final int TAIL_MATCH_START = 10;
    private static final int TAIL_MATCH_END = 17;
    private static final String TWO_TOKENS = "<<PII:A:1>><<PII:B:2>>zz";
    private static final int SECOND_TOKEN_START = 11;
    private static final int TRAILER_START = 22;
    private static final int TWO_TOKENS_END = 24;

    @Test
    void keepsAWindowEntirelyBeforeATokenUnchanged() {
        assertArrayEquals(new int[]{0, 3}, span(0, 3), "a window before the token is left as is");
    }

    @Test
    void keepsAWindowEntirelyAfterATokenUnchanged() {
        assertArrayEquals(
            new int[]{AFTER_TOKEN_START, AFTER_TOKEN_END},
            span(AFTER_TOKEN_START, AFTER_TOKEN_END), "a window after the token is left as is"
        );
    }

    @Test
    void trimsAWindowThatOverlapsTheTokenHead() {
        assertArrayEquals(new int[]{2, 4}, span(2, 8), "the overlap into the token head is trimmed off");
    }

    @Test
    void trimsAWindowThatOverlapsTheTokenTail() {
        assertArrayEquals(
            new int[]{TOKEN_END, TAIL_MATCH_END},
            span(TAIL_MATCH_START, TAIL_MATCH_END), "the overlap into the token tail is trimmed off"
        );
    }

    @Test
    void dropsAWindowThatStraddlesTheWholeToken() {
        assertTrue(
            TokenRegions.scan(TEXT).tokenFreeSpan(2, TAIL_MATCH_END).isEmpty(),
            "a window straddling the token is dropped"
        );
    }

    @Test
    void dropsAWindowFullyInsideTheToken() {
        assertTrue(TokenRegions.scan(TEXT).tokenFreeSpan(6, 9).isEmpty(), "a window inside the token is dropped");
    }

    @Test
    void keepsTheTailWhenAMatchStartsExactlyAtTheTokenStart() {
        assertArrayEquals(
            new int[]{TOKEN_END, TAIL_MATCH_END}, span(4, TAIL_MATCH_END),
            "a match beginning on the token's first char is advanced past the whole token"
        );
    }

    @Test
    void keepsTheTrailerWhenAMatchStartsAtASeamBetweenAdjacentTokens() {
        assertArrayEquals(
            new int[]{TRAILER_START, TWO_TOKENS_END},
            TokenRegions.scan(TWO_TOKENS).tokenFreeSpan(SECOND_TOKEN_START, TWO_TOKENS_END).orElseThrow(),
            "a match starting on the second token's first char is advanced past that token to the trailer"
        );
    }

    @Test
    void keepsTheHeadWhenAMatchEndsExactlyAtTheTokenEnd() {
        assertArrayEquals(
            new int[]{2, 4}, span(2, TOKEN_END),
            "a match ending on the token's last char is retracted to before the whole token"
        );
    }

    @Test
    void dropsAnEmptyWindowEvenWhenNoTokenIsPresent() {
        assertTrue(
            TokenRegions.scan("plain").tokenFreeSpan(3, 3).isEmpty(),
            "a zero-width window is never emitted, token or not"
        );
    }

    @Test
    void keepsTheFullWindowWhenNoTokenIsPresent() {
        assertArrayEquals(
            new int[]{0, 5}, TokenRegions.scan("plain").tokenFreeSpan(0, 5).orElseThrow(),
            "token-free text leaves the whole window intact"
        );
    }

    private static int[] span(int start, int end) {
        return TokenRegions.scan(TEXT).tokenFreeSpan(start, end).orElseThrow();
    }
}
