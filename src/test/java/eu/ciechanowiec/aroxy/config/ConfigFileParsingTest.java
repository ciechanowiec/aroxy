package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.ClaudeCode;
import eu.ciechanowiec.aroxy.client.Gateway;
import eu.ciechanowiec.aroxy.obfuscation.RuleKind;
import eu.ciechanowiec.aroxy.obfuscation.RuleSpec;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the stanza parser reads the bundled {@code default.conf}: every rule parses to the
 * expected {@link RuleSpec}, the {@code [gateway]} keys become a typed {@link Settings} overlay, and
 * comments and blank lines are ignored.
 */
@SuppressWarnings(
    "PMD.AvoidUsingHardCodedIP"
) // local addresses appear inside config-text fixtures
class ConfigFileParsingTest {

    private static final int DEFAULT_PORT = 8788;
    private static final int RULE_COUNT = 18;
    private static final int OVERRIDE_PORT = 9999;

    private static ConfigFile bundled() {
        return ConfigFile.parse("default.conf", DefaultConfigFixture.bundledText());
    }

    private static Map<String, RuleSpec> specsById() {
        return bundled().rules().stream().map(RuleEntry::spec)
            .collect(Collectors.toMap(RuleSpec::id, spec -> spec));
    }

    private static Gateway claudeCode(Settings settings) {
        return settings.gateway(ClaudeCode.ID);
    }

    private static RuleSpec spec(String id) {
        return Objects.requireNonNull(specsById().get(id));
    }

    @Test
    void parsesGatewaySettingsIntoDefaults() {
        Settings settings = bundled().gateway().applyTo(Settings.defaults());
        assertEquals("127.0.0.1", settings.listenHost(), "listen host");
        assertEquals(DEFAULT_PORT, claudeCode(settings).listenPort(), "listen port");
        assertEquals(
            URI.create("https://api.anthropic.com"), claudeCode(settings).upstream(), "upstream"
        );
        assertTrue(settings.maskSecrets(), "mask secrets default on");
    }

    @Test
    void parsesEveryDefaultRule() {
        List<String> ids = bundled().rules().stream().map(RuleEntry::id).toList();
        assertTrue(ids.contains("private_key"), "private_key parsed");
        assertTrue(ids.contains("phone"), "phone parsed");
        assertTrue(ids.contains("internal_url"), "internal_url parsed");
        assertEquals(RULE_COUNT, ids.size(), "all eighteen rules parsed");
    }

    @Test
    void parsesRegexRuleWithFlags() {
        RuleSpec secret = spec("generic_secret");
        assertEquals(RuleKind.REGEX, secret.kind(), "regex kind");
        assertEquals("i", secret.flags(), "ignore-case flag");
        assertTrue(secret.sensitive(), "secret is sensitive");
    }

    @Test
    void parsesLiteralLooseRule() {
        RuleSpec company = spec("company_acme");
        assertEquals(RuleKind.LITERAL, company.kind(), "literal kind");
        assertEquals("Acme Corporation", company.patternOrValue(), "verbatim value");
        assertTrue(company.loose(), "loose matching on");
        assertEquals("i", company.flags(), "ignore-case flag");
    }

    @Test
    void parsesDisabledFlag() {
        RuleEntry phone = bundled().rules().stream()
            .filter(entry -> "phone".equals(entry.id())).findFirst().orElseThrow();
        assertFalse(phone.enabled(), "phone ships disabled");
    }

    @Test
    void keepsRegexPatternVerbatim() {
        RuleSpec jwt = spec("jwt");
        assertTrue(jwt.patternOrValue().contains("\\."), "backslash kept verbatim, not unescaped");
    }

    @Test
    void ignoresCommentsAndBlankLines() {
        String text = "# a comment\n\n[gateway]\n\n  listen_claude = 10.0.0.1:9999  \n# trailing comment\n";
        Settings settings = ConfigFile.parse("inline", text).gateway().applyTo(Settings.defaults());
        assertEquals("127.0.0.1", settings.listenHost(), "host stays 127.0.0.1; the configured host is ignored");
        assertEquals(OVERRIDE_PORT, claudeCode(settings).listenPort(), "port parsed past comments");
    }

    @Test
    void neverBindsANetworkHost() {
        String text = "[gateway]\nlisten_claude = 0.0.0.0:8788\n";
        Settings settings = ConfigFile.parse("inline", text).gateway().applyTo(Settings.defaults());
        assertEquals("127.0.0.1", settings.listenHost(), "a network-facing host is ignored, not honored");
        assertEquals(
            DEFAULT_PORT, claudeCode(settings).listenPort(), "the port is still read from a host:port value"
        );
    }

    @Test
    void readsABarePortWithoutAHost() {
        String text = "[gateway]\nlisten_claude = 9999\n";
        Settings settings = ConfigFile.parse("inline", text).gateway().applyTo(Settings.defaults());
        assertEquals("127.0.0.1", settings.listenHost(), "host stays 127.0.0.1");
        assertEquals(OVERRIDE_PORT, claudeCode(settings).listenPort(), "a bare port is honored");
    }

    @Test
    void whitespaceOnlyFileYieldsNoRules() {
        ConfigFile empty = ConfigFile.parse("empty", "   \n\n#only a comment\n   \n");
        assertTrue(empty.rules().isEmpty(), "no rules from a blank file");
    }

    @Test
    void emptyIdStanzaIsSkippedAndOtherRulesSurvive() {
        String text = """
            [rule]
            id =
            placeholder = COMPANY
            type = literal
            value = Acme
            [rule]
            id = real
            placeholder = COMPANY
            type = literal
            value = Beta
            """;
        List<String> ids = ConfigFile.parse("inline", text).rules().stream().map(RuleEntry::id).toList();
        assertEquals(List.of("real"), ids, "an empty id skips its stanza instead of colliding on \"\"");
    }

    @Test
    void emptyPlaceholderStanzaIsSkippedNotWholeFile() {
        String text = """
            [rule]
            id = broken
            placeholder =
            type = literal
            value = Acme
            [rule]
            id = real
            placeholder = COMPANY
            type = literal
            value = Beta
            """;
        List<String> ids = ConfigFile.parse("inline", text).rules().stream().map(RuleEntry::id).toList();
        assertEquals(List.of("real"), ids, "an empty placeholder skips its stanza, the file still loads");
    }

    @Test
    void whitespaceOnlyLiteralValueYieldsNoRule() {
        String text = "[rule]\nid = blanky\nplaceholder = COMPANY\ntype = literal\nloose = true\nvalue =    \n";
        assertTrue(ConfigFile.parse("inline", text).rules().isEmpty(), "a blank value skips the stanza");
    }
}
