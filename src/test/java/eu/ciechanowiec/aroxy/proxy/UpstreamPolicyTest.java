package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.CodexCli;
import eu.ciechanowiec.aroxy.client.Gateway;
import eu.ciechanowiec.aroxy.client.PathScope;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Pins when an explicit forward-proxy destination may replace the configured test seam.
 */
class UpstreamPolicyTest {

    private static final URI UNREACHABLE = URI.create("http://127.0.0.1:1/chosen?value=one");

    @Test
    @SneakyThrows
    void keepsAChangedCopilotGatewayFixedOnItsTestSeam() {
        FakeUpstream server = new FakeUpstream(CannedReply.json("{}"));
        try {
            Gateway gateway = new Gateway(0, server.uri());
            send(Upstream.forProfile(gateway, CopilotFixtures.profile()));
            assertEquals("/chosen?value=one", server.lastTarget());
        } finally {
            server.stop();
        }
    }

    @Test
    @SneakyThrows
    void keepsABufferedClientFixedEvenWhenItsGatewayIsUnchanged() {
        FakeUpstream server = new FakeUpstream(CannedReply.json("{}"));
        try {
            Gateway gateway = new Gateway(0, server.uri());
            ClientProfile profile = new ClientProfile(
                new ClientId("fixed-test"), "Fixed test", gateway,
                new PathScope(List.of("/chosen"), "Fixed test"), CodexCli.transport()
            );
            send(Upstream.forProfile(gateway, profile));
            assertEquals("/chosen?value=one", server.lastTarget());
        } finally {
            server.stop();
        }
    }

    @SneakyThrows
    private static void send(Upstream upstream) {
        UpstreamReply reply = upstream.sendTo(UNREACHABLE, "GET", Map.of(), new byte[0]);
        try (InputStream body = reply.body()) {
            body.readAllBytes();
        }
    }
}
