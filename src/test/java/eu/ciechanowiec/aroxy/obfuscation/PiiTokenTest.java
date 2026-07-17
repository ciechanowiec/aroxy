package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Parsing the counter out of a token: a well-formed token yields its index, while a token missing the
 * separator, missing the closing marker, or carrying non-numeric digits falls back to zero rather than
 * throwing.
 */
class PiiTokenTest {

    @Test
    void readsTheIndexFromAWellFormedToken() {
        assertEquals(7, PiiToken.indexOf(PiiToken.build("AWS_KEY", 7)), "the numeric counter is parsed");
    }

    @Test
    void treatsATokenWithoutASeparatorAsZero() {
        assertEquals(0, PiiToken.indexOf("no-separator-here"), "a token without a colon parses as zero");
    }

    @Test
    void treatsATokenWithoutAClosingMarkerAsZero() {
        assertEquals(0, PiiToken.indexOf("<<PII:AWS:5"), "a token missing the closing marker parses as zero");
    }

    @Test
    void treatsNonNumericDigitsAsZero() {
        assertEquals(0, PiiToken.indexOf("<<PII:AWS:xy>>"), "a non-numeric counter parses as zero");
    }

    @Test
    void readsTheIndexWhenTheSeparatorSitsAtTheStart() {
        assertEquals(7, PiiToken.indexOf(":7>>"), "a separator at index zero is still a valid separator");
    }
}
