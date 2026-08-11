package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.BufferedTransport;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
import eu.ciechanowiec.aroxy.client.RequestBodyPolicy;
import eu.ciechanowiec.aroxy.client.SseDialect;
import eu.ciechanowiec.aroxy.daemon.Home;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the routing decision that leaves hosts outside Copilot's boundary as ordinary tunnels.
 */
@Justification(
    "the socket-level tunnel test legitimately composes transport, certificates, TLS, and an upstream"
)
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
class CopilotTunnelTest {

    private static final int HTTPS = 443;
    private static final int INTERNAL = 9443;

    @TempDir
    private Path base;

    @Test
    void keepsAnUnprotectedDestinationUnchanged() {
        BufferedTransport buffered = new BufferedTransport(
            RequestBodyPolicy.text(), new SseDialect(List.of(), Set.of(), Set.of())
        );
        CopilotTransport transport = new CopilotTransport(
            Set.of("githubcopilot.com"), RequestBodyPolicy.text(), buffered, buffered
        );
        InetSocketAddress intercepted = new InetSocketAddress(
            InetAddress.ofLiteral(LocalHost.HOST), INTERNAL
        );
        CopilotTunnel tunnel = new CopilotTunnel(
            transport, CopilotCertificates.open(new Home(this.base)),
            new SslContextFactory.Server(), intercepted
        );
        InetSocketAddress destination = tunnel.destination("github.com", HTTPS);
        assertEquals("github.com", destination.getHostString());
        assertEquals(HTTPS, destination.getPort());
    }

    @Test
    @SneakyThrows
    void tunnelsAnUnprotectedDestinationWithoutInterception() {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{}"));
        ProxyHarness proxy = new ProxyHarness(upstream, this.base, CopilotFixtures.profile());
        try (
            Socket socket = new Socket(proxy.base().getHost(), proxy.base().getPort());
            PrintWriter request = new PrintWriter(socket.getOutputStream(), false, StandardCharsets.US_ASCII);
            BufferedReader response = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
            )
        ) {
            String authority = LocalHost.HOST + ":" + upstream.uri().getPort();
            request.print("CONNECT " + authority + " HTTP/1.1\r\nHost: " + authority + "\r\n\r\n");
            request.flush();
            String status = Optional.ofNullable(response.readLine()).orElse("");
            assertTrue(status.contains(" 200 "), status);
        } finally {
            proxy.stop();
            upstream.stop();
        }
    }
}
