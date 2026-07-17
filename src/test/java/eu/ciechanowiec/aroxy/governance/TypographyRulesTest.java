package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The typography scan flags each banned code point with its line and column and ignores plain ASCII.
 */
class TypographyRulesTest {

    @Test
    void flagsEmDashWithLineAndColumn() {
        String content = "alpha " + Character.toString(TypographyRules.EM_DASH) + " beta";
        List<TypographyViolation> violations = TypographyRules.findViolations(content);
        assertEquals(1, violations.size());
        assertEquals(TypographyRules.EM_DASH, violations.getFirst().codePoint());
        assertEquals(7, violations.getFirst().column());
    }

    @Test
    void reportsLineNumbersAcrossNewlines() {
        String content = "clean line\nsecond " + Character.toString(TypographyRules.ELLIPSIS);
        List<TypographyViolation> violations = TypographyRules.findViolations(content);
        assertEquals(1, violations.size());
        assertEquals(2, violations.getFirst().lineNumber());
    }

    @Test
    void acceptsPlainAsciiEquivalents() {
        String content = "plain - hyphen, three periods ... and \"straight quotes\"";
        assertTrue(TypographyRules.findViolations(content).isEmpty());
    }

    @Test
    void flagsEveryBannedCodePoint() {
        List<Integer> banned = List.of(
            TypographyRules.EM_DASH, TypographyRules.EN_DASH, TypographyRules.ELLIPSIS,
            TypographyRules.LEFT_SINGLE_QUOTE, TypographyRules.RIGHT_SINGLE_QUOTE,
            TypographyRules.LEFT_DOUBLE_QUOTE, TypographyRules.RIGHT_DOUBLE_QUOTE,
            TypographyRules.LOW_DOUBLE_QUOTE
        );
        assertTrue(banned.stream().allMatch(TypographyRules::isBanned));
    }
}
