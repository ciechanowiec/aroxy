package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CursorCli;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cursor's buffered methods: the one whose body is transformed, and everything else, which is
 * forwarded as it stands. What the agent gets back has to be the upstream's own answer - its status,
 * its headers and its bytes - because the proxy is transparent about everything except the values it
 * replaces, and an answer that silently loses one of those three still looks like a reply.
 */
class CursorBufferedRoutesTest {

    private static final String APPEND = "/aiserver.v1.BidiService/BidiAppend";
    private static final String PROTO = "application/proto";
    private static final int CONFLICT = 409;

    // The transformed method reads protobuf. A body in some other shape cannot be walked, so it is
    // refused rather than forwarded unexamined.
    @Test
    @SneakyThrows
    void refusesAnInputStreamThatIsNotProtobuf(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.protobuf());
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            HttpResponse<String> response = send(
                harness, APPEND, "application/json", CursorWire.protobuf("payload")
            );
            assertEquals(Http.UNSUPPORTED_MEDIA_TYPE, response.statusCode());
            assertEquals(0, upstream.requestCount(), "nothing unreadable is forwarded to Cursor");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void returnsTheUpstreamStatusAndBodyOfTheTransformedMethod(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(
            new CannedReply(
                CONFLICT, Map.of("Content-Type", PROTO, "X-Cursor-Note", "kept"),
                CursorWire.text(CursorWire.protobuf("upstream said so"))
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            HttpResponse<String> response = send(
                harness, APPEND, PROTO, CursorWire.protobuf(CursorWire.protobuf("Ask about it"))
            );
            assertEquals(CONFLICT, response.statusCode(), "the upstream's status reaches the agent");
            assertEquals("kept", response.headers().firstValue("X-Cursor-Note").orElse(""));
            assertTrue(response.body().contains("upstream said so"), "and so do its bytes: " + response.body());
        } finally {
            harness.stop();
        }
    }

    // Everything outside the transformed methods is forwarded untouched, which includes leaving the
    // upstream's own content-encoding in place: the proxy did not decode the body, so it must not
    // claim the body is plain.
    @Test
    @SneakyThrows
    void preservesTheEncodingOfAForwardedReply(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(
            new CannedReply(
                Http.OK, Map.of("Content-Type", "application/json", "Content-Encoding", "gzip"),
                "{\"ok\":true}"
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            HttpResponse<String> response = send(harness, "/auth/login", "application/json", new byte[0]);
            assertEquals(
                "gzip", response.headers().firstValue("content-encoding").orElse(""),
                "a forwarded reply keeps the encoding it arrived with"
            );
        } finally {
            harness.stop();
        }
    }

    @SneakyThrows
    private static HttpResponse<String> send(
        ProxyHarness harness, String path, String contentType, byte[] body
    ) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(harness.base().resolve(path))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    // An empty reply still has a length, and saying so is what lets the agent stop reading. Treating
    // zero as "no length known" would leave it waiting on a body that never comes.
    @Test
    @SneakyThrows
    void declaresTheLengthOfAnEmptyReply(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.protobuf());
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            HttpResponse<String> response = send(
                harness, APPEND, PROTO, CursorWire.protobuf(CursorWire.protobuf("nothing to match"))
            );
            assertEquals(
                "0", response.headers().firstValue("content-length").orElse(""),
                "an empty transformed reply declares a length of zero"
            );
        } finally {
            harness.stop();
        }
    }
}
