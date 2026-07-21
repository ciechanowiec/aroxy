package eu.ciechanowiec.aroxy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the contract a registered client must satisfy, so a client added later is either complete or
 * fails the build rather than half-working. Every profile needs its own identity, its own port, and
 * its own configuration key; two clients sharing any of those would make one of them unreachable, and
 * the failure would surface as a confusing bind error rather than as a broken registration.
 */
class ClientRegistryTest {

    private static final int CLAUDE_CODE_PORT = 8788;
    private static final int CODEX_PORT = 8789;
    private static final int OTHER_PORT = 9999;

    @Test
    void everyRegisteredClientIsPresentExactlyOnce() {
        List<ClientId> ids = ClientRegistry.profiles().stream().map(ClientProfile::id).toList();
        assertEquals(Set.copyOf(ids).size(), ids.size(), "no client is registered twice");
        assertTrue(ids.contains(ClaudeCode.ID), "Claude Code is served");
        assertTrue(ids.contains(CodexCli.ID), "Codex is served");
    }

    @Test
    void noTwoClientsClaimTheSameDefaultPort() {
        List<Integer> ports = ClientRegistry.profiles().stream()
            .map(profile -> profile.gateway().listenPort())
            .toList();
        assertEquals(
            Set.copyOf(ports).size(), ports.size(),
            "a shared default port would leave one client unable to bind out of the box"
        );
    }

    @Test
    void noTwoClientsShareAConfigKeyOrAnUpstream() {
        List<String> keys = ClientRegistry.listenKeys();
        assertEquals(Set.copyOf(keys).size(), keys.size(), "each client is configured by its own key");
        assertEquals(ClientRegistry.profiles().size(), keys.size(), "every client contributes a key");
        assertNotEquals(
            ClaudeCode.profile().gateway().upstream(), CodexCli.profile().gateway().upstream(),
            "the two clients reach different APIs, which is why each needs its own listener"
        );
    }

    @Test
    void everyClientIsConfiguredByTheSameShapeOfKey() {
        assertEquals("listen_claude", ClaudeCode.ID.configKey(), "Claude Code takes its slug");
        assertEquals("listen_codex", CodexCli.ID.configKey(), "and so does Codex");
        assertTrue(
            ClientRegistry.profiles().stream()
                .map(ClientProfile::id)
                .allMatch(id -> id.configKey().equals("listen_" + id.slug())),
            "the key is built from the slug rather than written out, so no client can break the pattern"
        );
    }

    @Test
    void aClientIdIsOpenButConfigurationSafe() {
        ClientId addedLater = new ClientId("future-agent_2");
        assertEquals("listen_future-agent_2", addedLater.configKey(), "an arbitrary valid id forms its key");
        assertThrows(IllegalArgumentException.class, () -> new ClientId("Future Agent"));
        assertThrows(IllegalArgumentException.class, () -> new ClientId("2future"));
    }

    @Test
    void defaultGatewaysCoverEveryRegisteredClient() {
        Map<ClientId, Gateway> gateways = ClientRegistry.defaultGateways();
        assertEquals(ClientRegistry.profiles().size(), gateways.size(), "one gateway per client");
        assertEquals(CLAUDE_CODE_PORT, port(gateways, ClaudeCode.ID), "Claude Code keeps its port");
        assertEquals(CODEX_PORT, port(gateways, CodexCli.ID), "Codex takes its own port");
    }

    private static int port(Map<ClientId, Gateway> gateways, ClientId id) {
        return Optional.ofNullable(gateways.get(id)).orElseThrow().listenPort();
    }

    @Test
    void aGatewayCarriesItsPortAndUpstreamIndependently() {
        Gateway gateway = ClaudeCode.profile().gateway();
        assertEquals(OTHER_PORT, gateway.withPort(OTHER_PORT).listenPort(), "the port is repointed");
        assertEquals(
            gateway.upstream(), gateway.withPort(OTHER_PORT).upstream(), "repointing a port keeps the upstream"
        );
        assertEquals(gateway.listenPort(), gateway.withUpstream(gateway.upstream()).listenPort(), "and the reverse");
    }

    @Test
    void registrationRejectsDuplicateIdsAndPorts() {
        ClientProfile first = profile("first", OTHER_PORT);
        ClientProfile sameId = profile("first", OTHER_PORT + 1);
        ClientProfile samePort = profile("second", OTHER_PORT);
        assertThrows(IllegalStateException.class, () -> ClientRegistry.validated(List.of(first, sameId)));
        assertThrows(IllegalStateException.class, () -> ClientRegistry.validated(List.of(first, samePort)));
    }

    @Test
    void registrationKeepsDistinctProfilesInOrder() {
        ClientProfile first = profile("first", OTHER_PORT);
        ClientProfile second = profile("second", OTHER_PORT + 1);
        assertEquals(
            List.of(first, second), ClientRegistry.validated(List.of(first, second)),
            "validating a registry preserves every profile and its registration order"
        );
    }

    @Test
    void aProfileRejectsABlankDisplayName() {
        ClientProfile valid = profile("future", OTHER_PORT);
        assertThrows(
            IllegalArgumentException.class,
            () -> new ClientProfile(
                valid.id(), " ", valid.gateway(), valid.scope(), valid.requestBody(), valid.dialect()
            )
        );
    }

    private static ClientProfile profile(String slug, int port) {
        return new ClientProfile(
            new ClientId(slug), slug, new Gateway(port, URI.create("https://" + slug + ".example")),
            new PathScope(List.of("/responses"), "/responses"), RequestBodyPolicy.text(),
            new SseDialect(List.of(), Set.of(), Set.of())
        );
    }
}
