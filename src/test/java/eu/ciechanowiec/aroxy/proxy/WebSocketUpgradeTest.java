package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CodexCli;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A tunnelled frame protocol carries no body this proxy can obfuscate, so an upgrade must be refused.
 * Codex CLI tries one before falling back to Server-Sent Events, and only {@code 426} makes it fall
 * back at once instead of retrying visibly, so the status here is the difference between a client that
 * quietly works and one that looks broken.
 */
class WebSocketUpgradeTest {

    private static final int UPGRADE_REQUIRED = 426;

    @Test
    void anUpgradeHeaderIsRecognisedHoweverItIsCased() {
        assertTrue(WebSocketUpgrade.isRequested(Map.of("Upgrade", List.of("websocket"))), "the plain form");
        assertTrue(WebSocketUpgrade.isRequested(Map.of("upgrade", List.of("WebSocket"))), "mixed case");
    }

    @Test
    void anOrdinaryRequestIsNotAnUpgrade() {
        assertFalse(WebSocketUpgrade.isRequested(Map.of()), "no header at all");
        assertFalse(WebSocketUpgrade.isRequested(Map.of("Upgrade", List.of("h2c"))), "a different protocol");
        assertFalse(WebSocketUpgrade.isRequested(Map.of("Content-Type", List.of("websocket"))), "a different header");
    }

    /**
     * Driven over a raw socket rather than through {@link java.net.http.HttpClient}, which refuses to
     * set {@code Upgrade} at all: it is a restricted header there, so the very request under test
     * cannot be built with it.
     */
    @Test
    @SneakyThrows
    void anUpgradeRequestIsRefusedWithUpgradeRequiredAndNeverReachesTheUpstream(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CodexCli.profile());
        try {
            String statusLine = upgradeRequest(harness.base());
            assertTrue(
                statusLine.contains(String.valueOf(UPGRADE_REQUIRED)),
                "the one status a client falls back from immediately rather than retrying: " + statusLine
            );
            assertEquals("", upstream.lastBody(), "the upgrade is refused here, not forwarded");
        } finally {
            harness.stop();
        }
    }

    @SneakyThrows
    private static String upgradeRequest(URI base) {
        try (Socket socket = new Socket(base.getHost(), base.getPort())) {
            socket.getOutputStream().write(
                ("GET " + Http.RESPONSES + " HTTP/1.1\r\nHost: " + base.getAuthority() + "\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n\r\n").getBytes(StandardCharsets.UTF_8)
            );
            socket.getOutputStream().flush();
            return statusLineOf(socket);
        }
    }

    @SneakyThrows
    private static String statusLineOf(Socket socket) {
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            )
        ) {
            return Objects.requireNonNull(reader.readLine(), "the proxy answered with no status line");
        }
    }
}
