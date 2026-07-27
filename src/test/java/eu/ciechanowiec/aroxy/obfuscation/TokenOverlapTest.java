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

    @Test
    void obfuscatesTheTailOfAStaggeredOverlap() {
        Rule first = RuleFixtures.regex("first", "AWS_KEY", "ABCDE");
        Rule second = RuleFixtures.regex("second", "EMAIL", "DEFGH");
        Obfuscator obfuscator = new Obfuscator(List.of(first, second), Vault.inMemory());
        String input = "ABCDEFGH";
        ObfuscationResult result = obfuscator.obfuscate(input);
        assertEquals(
            List.of("ABCDE", "FGH"),
            result.matches().stream().map(ObfuscationMatch::original).toList(),
            "the accepted match and the exact uncovered tail are both tokenized"
        );
        assertEquals(input, obfuscator.deobfuscate(result.text()), "the staggered overlap must round-trip exactly");
    }

    // Two matches that touch without overlapping are both whole matches. Treating the second as a tail
    // of the first would key it on its raw surface instead of its canonical spelling, so a variant of a
    // value already in the vault would leave under a second, different token.
    @Test
    void keepsCanonicalKeyingForAMatchThatBeginsWhereTheLastOneEnds() {
        Obfuscator obfuscator = RuleFixtures.obfuscator(
            RuleCompiler.loose("company", "COMPANY", LiteralSpec.loose("Netscape"))
        );
        ObfuscationResult joined = obfuscator.obfuscate("NetscapeNet scape");
        assertEquals(2, joined.matches().size(), "adjacency does not swallow the second match");
        List<String> tokens = joined.matches().stream().map(ObfuscationMatch::token).distinct().toList();
        assertEquals(
            1, tokens.size(),
            "a spelling variant touching the canonical one still collapses onto the same token: " + tokens
        );
    }

    // A shorter match wholly inside one already accepted adds nothing: its span is covered. Admitting it
    // as a tail would mint a token for an empty remainder.
    @Test
    void dropsAMatchWhollyInsideOneAlreadyAccepted() {
        Obfuscator obfuscator = new Obfuscator(
            List.of(
                RuleFixtures.regex("full", "COMPANY", "ExampleCorp"),
                RuleFixtures.regex("part", "COMPANY", "Corp")
            ),
            Vault.inMemory()
        );
        ObfuscationResult result = obfuscator.obfuscate("ExampleCorp");
        assertEquals(
            1, result.matches().size(),
            "a match ending where the accepted one ends adds no second, empty match: " + result.matches()
        );
        assertFalse(result.text().contains("Corp"), result.text());
    }
}
