package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Verifies the layered merge: settings last-wins per key, rule override-by-id replaces content while
 * keeping position, new ids append, disabled rules are excluded, and emptying the default file leaves
 * only the later files' rules.
 */
class EffectiveConfigMergeTest {

    private static final int BASE_PORT = 8788;
    private static final String BASE = "base";
    private static final String COMPANY = "COMPANY";

    private static ConfigFile file(String label, String text) {
        return ConfigFile.parse(label, text);
    }

    private static String rule(String id, String placeholder, String pattern) {
        return "[rule]\nid = " + id + "\ntype = regex\nplaceholder = " + placeholder + "\npattern = " + pattern + "\n";
    }

    @Test
    void settingsLastWinsPerKey() {
        ConfigFile base = file(BASE, "[gateway]\nlisten = 127.0.0.1:8788\nmask_secrets = true\n");
        ConfigFile over = file("over", "[gateway]\nmask_secrets = false\n");
        Settings settings = EffectiveConfig.merge(List.of(base, over)).settings();
        assertEquals(BASE_PORT, settings.listenPort(), "untouched key keeps base value");
        assertFalse(settings.maskSecrets(), "later file wins mask_secrets");
        assertEquals(
            URI.create("https://api.anthropic.com"), settings.upstream(),
            "the upstream is fixed and no config key can move it"
        );
    }

    @Test
    void laterFileOverridesRuleInPlace() {
        ConfigFile base = file(BASE, rule("a", COMPANY, "aaa") + rule("b", "PROJECT", "bbb"));
        ConfigFile over = file("over", rule("a", COMPANY, "zzz"));
        List<Rule> rules = EffectiveConfig.merge(List.of(base, over)).rules();
        assertEquals(2, rules.size(), "override replaces, not appends");
        assertEquals("a", rules.getFirst().id(), "overridden rule keeps first position");
        assertEquals("b", rules.get(1).id(), "second rule keeps its position");
    }

    @Test
    void newIdsAppend() {
        ConfigFile base = file(BASE, rule("a", COMPANY, "aaa"));
        ConfigFile over = file("over", rule("c", "PROJECT", "ccc"));
        List<Rule> rules = EffectiveConfig.merge(List.of(base, over)).rules();
        assertEquals(List.of("a", "c"), rules.stream().map(Rule::id).toList(), "new id appended after existing");
    }

    @Test
    void disabledRulesExcluded() {
        ConfigFile base = file(BASE, rule("a", COMPANY, "aaa"));
        ConfigFile off = file(
            "off",
            "[rule]\nid = a\nenabled = false\ntype = regex\nplaceholder = COMPANY\npattern = aaa\n"
        );
        List<Rule> rules = EffectiveConfig.merge(List.of(base, off)).rules();
        assertTrue(rules.isEmpty(), "re-defining an id as disabled drops it");
    }

    @Test
    void emptyingDefaultLeavesOnlyLaterRules() {
        ConfigFile emptied = file("default", "[gateway]\n");
        ConfigFile later = file("session", rule("only", COMPANY, "xxx"));
        EffectiveConfig effective = EffectiveConfig.merge(List.of(emptied, later));
        assertEquals(1, effective.rules().size(), "only the later file contributes a rule");
        assertEquals("only", effective.rules().getFirst().id(), "the later rule survives");
    }

    @Test
    void sourcesListContributingLabels() {
        EffectiveConfig effective = EffectiveConfig.merge(List.of(file("one", ""), file("two", "")));
        assertEquals(List.of("one", "two"), effective.sources(), "contributing labels retained in order");
    }

    @Test
    void withUpstreamRepointsTheUpstreamKeepingRulesAndSources() {
        EffectiveConfig base = EffectiveConfig.merge(List.of(file(BASE, rule("solo", COMPANY, "aaa"))));
        EffectiveConfig pointed = base.withUpstream(URI.create("http://proxy.internal:1234"));
        assertEquals(
            URI.create("http://proxy.internal:1234"), pointed.settings().upstream(), "the upstream is repointed"
        );
        assertEquals(List.of("solo"), pointed.rules().stream().map(Rule::id).toList(), "rules are retained unchanged");
        assertEquals(List.of(BASE), pointed.sources(), "sources are retained unchanged");
    }

    @Test
    void withEphemeralPortHandsThePortChoiceToTheOsKeepingRulesAndSources() {
        ConfigFile base = file(BASE, "[gateway]\nlisten = 127.0.0.1:8788\n" + rule("solo", COMPANY, "aaa"));
        EffectiveConfig freed = EffectiveConfig.merge(List.of(base)).withEphemeralPort();
        assertEquals(0, freed.settings().listenPort(), "port 0 lets the OS pick a free port");
        assertEquals(List.of("solo"), freed.rules().stream().map(Rule::id).toList(), "rules are retained unchanged");
        assertEquals(List.of(BASE), freed.sources(), "sources are retained unchanged");
        assertEquals(
            URI.create("https://api.anthropic.com"), freed.settings().upstream(), "the upstream is untouched"
        );
    }

    @Test
    void disabledRuleNeverObfuscates() {
        String disabled = "[rule]\nid = a\nenabled = false\ntype = regex\nplaceholder = COMPANY\npattern = secret\n";
        ConfigFile off = file("off", disabled);
        Obfuscator obfuscator = new Obfuscator(
            EffectiveConfig.merge(List.of(off)).rules(), new Vault(Optional.empty())
        );
        assertEquals("a secret here", obfuscator.obfuscate("a secret here").text(), "disabled rule does nothing");
    }
}
