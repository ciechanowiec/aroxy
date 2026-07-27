package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CursorCli;
import java.nio.file.Path;
import java.util.Arrays;
import lombok.SneakyThrows;
import org.eclipse.jetty.client.BytesRequestContent;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.transport.HttpClientTransportOverHTTP2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that Cursor's native Run method reaches aroxy over HTTP/2 prior knowledge.
 */
class CursorHttp2Test {

    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String NATIVE_RUN = "/agent.v1.AgentService/Run";
    private static final String CONNECT_PROTO = "application/connect+proto";

    @Test
    @SneakyThrows
    void servesTheNativeRunMethodOverHttp2(@TempDir Path logs) {
        byte[] terminal = CursorWire.framed(ConnectJsonFrame.END_STREAM, "{}");
        byte[] response = CursorWire.concat(
            CursorWire.frame(CursorWire.protobuf(CursorWire.protobuf(TOKEN))), terminal
        );
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connectProtobuf(CursorWire.text(response))
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            byte[] request = CursorWire.frame(
                CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))
            );
            ContentResponse reply = send(harness, request);
            assertEquals(HttpVersion.HTTP_2, reply.getVersion());
            assertFalse(upstream.lastBody().contains(ProxyHarness.SECRET));
            assertTrue(upstream.lastBody().contains(TOKEN), upstream.lastBody());
            assertTrue(upstream.lastHeader("connect-accept-encoding").isEmpty());
            assertTrue(
                CursorWire.text(reply.getContent()).contains(ProxyHarness.SECRET),
                CursorWire.text(reply.getContent())
            );
            byte[] ending = Arrays.copyOfRange(
                reply.getContent(), reply.getContent().length - terminal.length,
                reply.getContent().length
            );
            assertArrayEquals(terminal, ending, "Connect end-stream metadata remains unchanged");
        } finally {
            harness.stop();
        }
    }

    @SneakyThrows
    private static ContentResponse send(ProxyHarness harness, byte[] body) {
        HTTP2Client transport = new HTTP2Client();
        try (
            HttpClient client = new HttpClient(new HttpClientTransportOverHTTP2(transport))
        ) {
            client.start();
            return client.POST(harness.base().resolve(NATIVE_RUN))
                .headers(
                    headers -> {
                        headers.put("Content-Type", CONNECT_PROTO);
                        headers.put("connect-accept-encoding", "gzip");
                    }
                )
                .body(new BytesRequestContent(body))
                .send();
        }
    }
}
