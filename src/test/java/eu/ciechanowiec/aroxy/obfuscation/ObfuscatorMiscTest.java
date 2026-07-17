package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Covers edge behaviours: text without any token, a disabled rule, an unknown token left untouched, an accumulation
 * across several rules, and the loose-pattern compiler output.
 */
class ObfuscatorMiscTest {

    @Test
    void deobfuscateReturnsTextWithoutTokenUnchanged() {
        Obfuscator obfuscator = new Obfuscator(List.of(), Vault.inMemory());
        assertEquals("nothing here", obfuscator.deobfuscate("nothing here"), "no token marker, no work");
    }

    @Test
    void leavesUnknownTokenUntouched() {
        Obfuscator obfuscator = new Obfuscator(List.of(), Vault.inMemory());
        assertEquals("<<PII:X:7>>", obfuscator.deobfuscate("<<PII:X:7>>"), "unknown token preserved");
    }

    @Test
    void disabledRuleDoesNothing() {
        RulePattern rp = new RulePattern(Pattern.compile("secret"));
        Rule disabled = new Rule("r", false, rp, RuleLabel.regex("S", true));
        Obfuscator obfuscator = new Obfuscator(List.of(disabled), Vault.inMemory());
        ObfuscationResult result = obfuscator.obfuscate("a secret value");
        assertEquals("a secret value", result.text(), "disabled rule is skipped");
        assertTrue(result.matches().isEmpty(), "no match");
    }

    @Test
    void accumulatesMatchesAcrossMultipleRules() {
        Rule aws = RuleFixtures.regex("aws", "AWS_KEY", "AKIA[0-9A-Z]{16}");
        Rule company = RuleCompiler.exact("company", "COMPANY", "Acme");
        Obfuscator obfuscator = new Obfuscator(List.of(aws, company), Vault.inMemory());
        String input = "Key AKIAIOSFODNN7EXAMPLE for Acme.";
        ObfuscationResult result = obfuscator.obfuscate(input);
        assertEquals(2, result.matches().size(), "both rules fired");
        assertEquals(input, obfuscator.deobfuscate(result.text()), "both restore");
    }

    @Test
    void laterRuleDoesNotCorruptAnEarlierToken() {
        Rule company = RuleCompiler.exact("company", "COMPANY", "Acme");
        Rule digits = RuleFixtures.regex("num", "NUM", "\\d+");
        Obfuscator obfuscator = new Obfuscator(List.of(company, digits), Vault.inMemory());
        String input = "I work at Acme.";
        ObfuscationResult result = obfuscator.obfuscate(input);
        assertEquals(1, result.matches().size(), "only the company rule fires; the token digits are protected");
        assertEquals(input, obfuscator.deobfuscate(result.text()), "the digit rule never rewrites the token counter");
    }

    @Test
    void loosePatternDropsWhitespaceAndAllowsSeparators() {
        String pattern = RuleCompiler.loosePattern("AB", false);
        assertTrue(pattern.contains("[\\s._-]*"), "separator class between chars");
        assertTrue(
            Pattern.compile(pattern).matcher("A.B").find(),
            "matches a separated spelling"
        );
    }

    @Test
    void valueObjectsExposeTheirParts() {
        RuleLabel label = new RuleLabel("P", true, Optional.of("canon"));
        assertEquals("canon", label.vaultKey("surface"), "canonical wins as vault key");
        assertEquals("surface", RuleLabel.regex("P", false).vaultKey("surface"), "regex keys by surface");
    }
}
