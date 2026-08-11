package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ServerSocket;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the public Copilot connector honors its configured listen port.
 */
class CopilotProxyListenerTest {

    @Test
    @SneakyThrows
    void bindsTheConfiguredPublicPort(@TempDir Path base) {
        int port = availablePort();
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{}"));
        ProxyHarness proxy = new ProxyHarness(upstream, base, CopilotFixtures.profile(), port);
        try {
            assertEquals(port, proxy.base().getPort());
        } finally {
            proxy.stop();
        }
    }

    @SneakyThrows
    private static int availablePort() {
        try (ServerSocket available = new ServerSocket(0)) {
            return available.getLocalPort();
        }
    }
}
