package eu.ciechanowiec.aroxy.obfuscation;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Builds the shared regex rules for the tests, so each test file reuses one definition rather than repeating the
 * patterns.
 */
@UtilityClass
final class RuleFixtures {

    static Rule regex(String id, String placeholder, String pattern) {
        RulePattern rulePattern = new RulePattern(Pattern.compile(pattern));
        return Rule.enabled(id, rulePattern, RuleLabel.regex(placeholder, true));
    }

    static Obfuscator obfuscator(Rule rule) {
        return new Obfuscator(List.of(rule), Vault.inMemory());
    }
}
