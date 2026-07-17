package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.obfuscation.RuleSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reads a single {@code [rule]} stanza's key/value pairs into a {@link RuleEntry}. The boolean flags
 * default to their off state when absent, an explicit {@code false} stays false rather than flipping
 * on, and an explicit {@code enabled = true} is honored rather than being silently disabled.
 */
class RuleReaderTest {

    private static final String ID = "id";
    private static final String PLACEHOLDER = "placeholder";
    private static final String PATTERN = "pattern";
    private static final String COMPANY = "COMPANY";
    private static final String LABEL = "stanza";

    private static RuleSpec specFrom(Map<String, String> keys) {
        return new RuleReader(LABEL, keys).read().orElseThrow().spec();
    }

    @Test
    void absentBooleanFlagsDefaultToOff() {
        RuleSpec spec = specFrom(Map.of(ID, "x", PLACEHOLDER, COMPANY, PATTERN, "abc"));
        assertFalse(spec.sensitive(), "sensitive defaults off");
        assertFalse(spec.loose(), "loose defaults off");
        assertFalse(spec.leet(), "leet defaults off");
    }

    @Test
    void anExplicitFalseFlagStaysFalse() {
        RuleSpec spec = specFrom(Map.of(ID, "x", PLACEHOLDER, COMPANY, PATTERN, "abc", "sensitive", "false"));
        assertFalse(spec.sensitive(), "an explicit sensitive = false is not read as true");
    }

    @Test
    void anExplicitEnabledTrueIsHonored() {
        RuleEntry entry = new RuleReader(
            LABEL, Map.of(ID, "x", PLACEHOLDER, COMPANY, PATTERN, "abc", "enabled", "true")
        ).read().orElseThrow();
        assertTrue(entry.enabled(), "an explicit enabled = true keeps the rule enabled");
    }
}
