package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Masking reveals only the first and last two characters plus the length of a long value, and fully hides a short one.
 */
class MaskedSecretTest {

    @Test
    void masksLongValueToHeadTailAndLength() {
        assertEquals("AK...LE (20 chars)", MaskedSecret.mask("AKIAIOSFODNN7EXAMPLE"));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"a", "ab", "abc", "abcd"}
    )
    void fullyMasksShortValue(String value) {
        assertEquals("****", MaskedSecret.mask(value));
    }

    @Test
    void revealsExactlyAtThreshold() {
        assertEquals("ab...de (5 chars)", MaskedSecret.mask("abcde"));
    }

    @Test
    void masksSensitiveMatchOriginalButLeavesPlainOriginalVisible() {
        ObfuscationMatch sensitive = new ObfuscationMatch("r", "P", "AKIAIOSFODNN7EXAMPLE", "<<PII:P:1>>", true);
        ObfuscationMatch plain = new ObfuscationMatch("r", "P", "jane.doe@acme.com", "<<PII:P:1>>", false);
        assertEquals("AK...LE (20 chars)", sensitive.maskedOriginal());
        assertEquals("jane.doe@acme.com", plain.maskedOriginal());
    }
}
