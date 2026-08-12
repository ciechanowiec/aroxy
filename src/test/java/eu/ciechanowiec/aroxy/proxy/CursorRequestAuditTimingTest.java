package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CursorCli;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.HttpClientTransportOverHTTP2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that a transformed Cursor request is audited before the upstream response reaches EOF.
 */
class CursorRequestAuditTimingTest {

    private static final String RUN = "/agent.v1.AgentService/Run";
    private static final String CONNECT_PROTO = "application/connect+proto";
    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    @SneakyThrows
    void auditsARequestBeforeUpstreamEof(@TempDir Path logs) {
        byte[] terminal = CursorWire.framed(ConnectJsonFrame.END_STREAM, "{}");
        byte[] response = CursorWire.concat(
            CursorWire.frame(CursorWire.protobuf(CursorWire.protobuf(TOKEN))), terminal
        );
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connectProtobuf(CursorWire.text(response)), ReplyEnding.HELD_OPEN
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        HTTP2Client transport = new HTTP2Client();
        try (
            HttpClient client = new HttpClient(new HttpClientTransportOverHTTP2(transport));
            AuditWatcher audit = new AuditWatcher(logs)
        ) {
            client.start();
            FutureTask<Boolean> exchange = exchange(client, harness);
            Thread.startVirtualThread(exchange);
            assertTrue(
                upstream.awaitResponsePayload(WAIT),
                "the upstream wrote its response body"
            );
            assertTrue(
                audit.await(TOKEN, WAIT),
                () -> "the request audit precedes upstream EOF; " + audit.contents()
            );
            assertFalse(exchange.isDone(), "the HTTP exchange is still waiting for upstream EOF");
            upstream.releaseResponseEof();
            exchange.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        } finally {
            upstream.releaseResponseEof();
            harness.stop();
        }
    }

    private static FutureTask<Boolean> exchange(HttpClient client, ProxyHarness harness) {
        return new FutureTask<>(
            () -> {
                byte[] request = CursorWire.frame(
                    CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))
                );
                client.POST(harness.base().resolve(RUN))
                    .headers(headers -> headers.put("Content-Type", CONNECT_PROTO))
                    .body(new BytesRequestContent(request))
                    .send();
                return true;
            }
        );
    }
}
