package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * When a later rule's match straddles a token an earlier rule already minted, only the token itself must be preserved
 * verbatim: the sensitive part of the match that lies outside the token has to be obfuscated too, not emitted in the
 * clear. A rule that matches entirely inside a token is still left untouched so the token stays reversible.
 */
class TokenOverlapTest {

    @Test
    void obfuscatesTheSensitivePartOfAMatchThatStraddlesAnEarlierToken() {
        Rule first = RuleFixtures.regex("first", "AWS_KEY", "TOKENA");
        Rule second = RuleFixtures.regex("second", "EMAIL", ">-SECRETB");
        Obfuscator obfuscator = new Obfuscator(List.of(first, second), Vault.inMemory());
        String input = "TOKENA-SECRETB";
        ObfuscationResult result = obfuscator.obfuscate(input);
        assertFalse(result.text().contains("SECRETB"), "the part outside the earlier token must not leak");
        assertEquals(input, obfuscator.deobfuscate(result.text()), "the round trip still restores exactly");
    }

    @Test
    void leavesAMatchThatFallsEntirelyInsideATokenUntouched() {
        Rule first = RuleFixtures.regex("first", "AWS_KEY", "TOKENA");
        Rule digits = RuleFixtures.regex("digits", "EMAIL", "\\d+");
        Obfuscator obfuscator = new Obfuscator(List.of(first, digits), Vault.inMemory());
        String input = "TOKENA and 4242";
        ObfuscationResult result = obfuscator.obfuscate(input);
        assertTrue(
            result.text().contains("<<PII:AWS_KEY:1>>"),
            "the existing token is intact, so its index was not re-tokenized"
        );
        assertFalse(result.text().contains("4242"), "the digits outside the token are obfuscated");
        assertEquals(input, obfuscator.deobfuscate(result.text()), "the round trip still restores exactly");
    }
}
