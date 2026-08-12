package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CursorCli;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An agent that cancels a turn drops the socket while aroxy is still streaming the reply into it. That
 * is routine, not a proxy fault, so Cursor's listener has to log it and carry on rather than error.
 * The buffered listener has its own guarantee. The Cursor router handles its failures separately, so
 * the property is proven against its socket too.
 *
 * <p>The upstream holds its response open until the test releases it, so the client is gone before the
 * last bytes are written and the failure is forced rather than raced for.
 *
 * <p>The reply is deliberately larger than a socket buffer. A short one is handed to the kernel and
 * accepted even though the peer has gone, so the proxy never learns anything and the very path this
 * test exists for is not the one it takes. Only a reply that has to keep writing runs into the closed
 * connection and produces the failure the router is meant to recognise as routine.
 */
class CursorDisconnectTest {

    private static final Duration PAYLOAD_TIMEOUT = Duration.ofSeconds(5);
    private static final int FRAMES = 5_000;
    private static final String PADDING = "x".repeat(80);

    @Test
    @SneakyThrows
    void anAgentDroppingTheStreamDoesNotErrorTheListener(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.connectProtobuf(bulkReply()), ReplyEnding.HELD_OPEN);
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            disconnectMidStream(harness.base(), upstream);
            assertEquals(
                Http.OK, follow(harness.base()),
                "the listener keeps serving after an agent drops its stream"
            );
            assertTrue(upstream.requestCount() > 1, "the follow-up still reaches the upstream");
        } finally {
            harness.stop();
        }
    }

    private static String bulkReply() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int index = 0; index < FRAMES; index++) {
            body.writeBytes(CursorWire.frame(CursorWire.textDelta("chunk " + index + " " + PADDING)));
        }
        return CursorWire.text(body.toByteArray());
    }

    @SneakyThrows
    private static void disconnectMidStream(URI base, FakeUpstream upstream) {
        try (Socket socket = new Socket(base.getHost(), base.getPort())) {
            writeRunRequest(socket.getOutputStream());
            upstream.awaitResponsePayload(PAYLOAD_TIMEOUT);
        }
        upstream.releaseResponseEof();
    }

    @SneakyThrows
    private static void writeRunRequest(OutputStream out) {
        byte[] body = CursorWire.concat(
            CursorWire.frame(CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))),
            CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
        );
        out.write(
            ("POST " + CursorCli.RUN + " HTTP/1.1\r\nHost: x\r\n"
                + "Content-Type: application/connect+proto\r\n"
                + "Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8)
        );
        out.write(body);
        out.flush();
    }

    @SneakyThrows
    private static int follow(URI base) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(base.resolve("/auth/login"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
