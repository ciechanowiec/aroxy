package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.ClaudeCode;
import eu.ciechanowiec.aroxy.client.CodexCli;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies per-agent gateway settings and validation after configuration files are merged.
 */
class AgentGatewayConfigTest {

    private static final int SPREAD_PORT = 9000;
    private static final String BASE = "base";
    private static final String RULE = "[rule]\nid = solo\ntype = regex\nplaceholder = COMPANY\npattern = aaa\n";

    @Test
    void rejectsTwoClientsConfiguredOntoTheSamePort() {
        ConfigFile clash = file(BASE, "[gateway]\nlisten_claude = 8788\nlisten_codex = 8788\n");
        InvalidConfigException rejected = assertThrows(
            InvalidConfigException.class, () -> EffectiveConfig.merge(List.of(clash)),
            "each client binds its own listener, so two on one port could never both start"
        );
        assertTrue(rejected.getMessage().contains("8788"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("Claude Code"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("Codex CLI"), rejected.getMessage());
    }

    @Test
    void rejectsAPortClashThatOnlyAppearsOnceTheFilesAreMerged() {
        ConfigFile base = file(BASE, "[gateway]\nlisten_claude = 9000\n");
        ConfigFile over = file("over", "[gateway]\nlisten_codex = 9000\n");
        assertThrows(
            InvalidConfigException.class, () -> EffectiveConfig.merge(List.of(base, over)),
            "neither file is wrong alone, which is why the clash is caught after the merge"
        );
    }

    @Test
    void acceptsDistinctPortsPerClient() {
        ConfigFile spread = file(BASE, "[gateway]\nlisten_claude = 9000\nlisten_codex = 9001\n");
        Settings settings = EffectiveConfig.merge(List.of(spread)).settings();
        assertEquals(
            SPREAD_PORT, settings.gateway(ClaudeCode.ID).listenPort(), "each client keeps the port it was given"
        );
        assertEquals(
            SPREAD_PORT + 1, settings.gateway(CodexCli.ID).listenPort(), "and the other keeps its own"
        );
    }

    @Test
    void withUpstreamRepointsOnlyTheNamedClient() {
        EffectiveConfig base = EffectiveConfig.merge(List.of(file(BASE, RULE)));
        URI standIn = URI.create("http://stand-in.internal:1234");
        EffectiveConfig pointed = base.withUpstream(CodexCli.ID, standIn);
        assertEquals(standIn, pointed.settings().gateway(CodexCli.ID).upstream(), "the named client moves");
        assertEquals(
            URI.create("https://api.anthropic.com"), pointed.settings().gateway(ClaudeCode.ID).upstream(),
            "and every other client stays on its own fixed API, which is what lets a test tell them apart"
        );
        assertEquals(List.of("solo"), pointed.rules().stream().map(Rule::id).toList(), "rules are unchanged");
    }

    @Test
    void aClientWithNoConfiguredGatewayIsReportedRatherThanReadAsAbsent() {
        Settings incomplete = new Settings(Settings.defaults().listenHost(), Map.of(), true);
        assertThrows(
            IllegalStateException.class, () -> incomplete.gateway(ClaudeCode.ID),
            "a listener with no gateway would otherwise bind nothing while the daemon reported itself up"
        );
    }

    private static ConfigFile file(String label, String text) {
        return ConfigFile.parse(label, text);
    }
}
