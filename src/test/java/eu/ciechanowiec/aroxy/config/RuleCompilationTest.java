package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Compiles rules through the effective configuration and exercises them end-to-end with a real
 * {@link Obfuscator}: a loose literal unifies spelling variants onto a single canonical token.
 */
class RuleCompilationTest {

    private static final Pattern TOKEN = Pattern.compile("<<PII:[A-Za-z0-9_]+:\\d+>>");

    private static Obfuscator obfuscatorFrom(String text) {
        List<Rule> rules = EffectiveConfig.merge(List.of(ConfigFile.parse("test", text))).rules();
        return new Obfuscator(rules, new Vault(Optional.empty()));
    }

    private static Set<String> tokens(CharSequence text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    @Test
    void looseLiteralObfuscatesVariantAndUnifies() {
        String text = "[rule]\nid = company\ntype = literal\nplaceholder = COMPANY\nloose = true\nvalue = Studebaker\n";
        Obfuscator obfuscator = obfuscatorFrom(text);
        String body = "Studebaker and stude-baker and STUDEBAKER together";
        String obfuscated = obfuscator.obfuscate(body).text();
        assertFalse(obfuscated.contains("Studebaker"), "spelling variant obfuscated");
        assertEquals(1, tokens(obfuscated).size(), "all variants collapse onto one token");
        assertTrue(obfuscator.deobfuscate(obfuscated).contains("Studebaker"), "restores canonical spelling");
    }

    @Test
    void exactLiteralMatchesOnlyTheVerbatimSpelling() {
        String text = "[rule]\nid = company\ntype = literal\nplaceholder = COMPANY\nvalue = Studebaker\n";
        Obfuscator obfuscator = obfuscatorFrom(text);
        String obfuscated = obfuscator.obfuscate("Studebaker here").text();
        assertFalse(obfuscated.contains("Studebaker"), "the verbatim spelling is obfuscated");
        assertEquals(1, tokens(obfuscated).size(), "exactly one token minted");
        assertEquals(
            "stude-baker here", obfuscator.obfuscate("stude-baker here").text(),
            "an exact literal does not match a separated respelling the way a loose rule would"
        );
    }

    @Test
    void regexRuleObfuscatesWholeMatchAndRestores() {
        String text = """
            [rule]
            id = secret
            type = regex
            placeholder = SECRET
            flags = i
            pattern = (?:password|token)\\s*=\\s*[^\\s]+
            """;
        Obfuscator obfuscator = obfuscatorFrom(text);
        String obfuscated = obfuscator.obfuscate("PASSWORD=hunter2 here").text();
        assertFalse(obfuscated.contains("hunter2"), "the whole assignment is obfuscated");
        assertEquals(1, tokens(obfuscated).size(), "one token");
        assertEquals("PASSWORD=hunter2 here", obfuscator.deobfuscate(obfuscated), "round-trips exactly");
    }
}
