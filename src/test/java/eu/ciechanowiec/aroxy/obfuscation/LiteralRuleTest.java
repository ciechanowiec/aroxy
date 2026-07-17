package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exercises literal rules: exact spelling, and loose/leet variants that must collapse onto one canonical token and
 * restore to the canonical spelling.
 */
class LiteralRuleTest {

    private static final String PREFIX = "{\"messages\":[{\"role\":\"user\",\"content\":\"I work at ";
    private static final String SUFFIX = ".\"}]}";
    private static final String NETSCAPE = "Netscape";

    private static String body(String name) {
        return PREFIX + name + SUFFIX;
    }

    static Stream<Arguments> variants() {
        return Stream.of(
            Arguments.of(LiteralSpec.loose(NETSCAPE), "Net scape"),
            Arguments.of(LiteralSpec.loose(NETSCAPE), "N.e.t.s.c.a.p.e"),
            Arguments.of(LiteralSpec.leet(NETSCAPE), "Ne7scape")
        );
    }

    private static Set<String> distinctTokens(CharSequence text) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = PiiToken.TOKEN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    @Test
    void roundTripsExactCompanyName() {
        Rule rule = RuleCompiler.exact("company_name", "COMPANY", "Acme Corporation");
        Obfuscator obfuscator = RuleFixtures.obfuscator(rule);
        String input = body("Acme Corporation");
        ObfuscationResult obfuscated = obfuscator.obfuscate(input);
        assertFalse(obfuscated.text().contains("Acme Corporation"), "value must be gone");
        assertTrue(obfuscated.text().contains(PiiToken.OPEN), "a token must be present");
        assertEquals(input, obfuscator.deobfuscate(obfuscated.text()), "exact spelling restores");
    }

    @ParameterizedTest
    @MethodSource(
        "variants"
    )
    void restoresVariantToCanonicalSpelling(LiteralSpec spec, String variant) {
        Rule rule = RuleCompiler.loose("company_variant", "COMPANY", spec);
        Obfuscator obfuscator = RuleFixtures.obfuscator(rule);
        String input = body(variant);
        ObfuscationResult obfuscated = obfuscator.obfuscate(input);
        assertFalse(obfuscated.text().contains(variant), "variant surface must be gone");
        assertEquals(
            body(spec.value()), obfuscator.deobfuscate(obfuscated.text()),
            "must restore the canonical spelling"
        );
    }

    @Test
    void spellingVariantsShareOneToken() {
        Rule rule = RuleCompiler.loose("company_atari", "COMPANY", LiteralSpec.loose("Atari"));
        Obfuscator obfuscator = new Obfuscator(List.of(rule), Vault.inMemory());
        String input = "{\"messages\":[{\"role\":\"user\",\"content\":"
            + "\"Atari and atari and ATARI and At-ari are all the same.\"}]}";
        ObfuscationResult obfuscated = obfuscator.obfuscate(input);
        assertEquals(1, distinctTokens(obfuscated.text()).size(), "all variants share one token");
        assertTrue(
            obfuscator.deobfuscate(obfuscated.text()).contains("Atari"),
            "token restores to the canonical name"
        );
    }
}
