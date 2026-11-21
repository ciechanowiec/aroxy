package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CursorCli;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives Cursor's protobuf compatibility transport over real client, proxy, and upstream sockets.
 */
class CursorConnectTest {

    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String RUN = "/agent.v1.AgentService/RunSSE";
    private static final String APPEND = "/aiserver.v1.BidiService/BidiAppend";
    private static final String CONNECT_JSON = "application/connect+json";
    private static final String CONNECT_PROTO = "application/connect+proto";
    private static final String PROTO = "application/proto";

    @Test
    @SneakyThrows
    void transformsCursorConnectProtobufFrames(@TempDir Path logs) {
        byte[] response = CursorWire.frame(
            CursorWire.protobuf(CursorWire.protobuf(TOKEN))
        );
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connectProtobuf(CursorWire.text(response))
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            byte[] request = CursorWire.concat(
                CursorWire.frame(
                    CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))
                ),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            );
            byte[] restored = send(harness, request, CONNECT_PROTO);
            assertFalse(upstream.lastBody().contains(ProxyHarness.SECRET));
            assertTrue(upstream.lastBody().contains(TOKEN), upstream.lastBody());
            assertTrue(
                CursorWire.text(restored).contains(ProxyHarness.SECRET),
                CursorWire.text(restored)
            );
        } finally {
            harness.stop();
        }
    }

    // Cursor chunks its visible text at roughly eleven characters, shorter than a token, so a token
    // spanning two protobuf frames is the normal case rather than an edge case. Before the carry
    // buffer reached this path every such token reached the terminal unreversed.
    @Test
    @SneakyThrows
    void restoresATokenSplitAcrossCursorProtobufDeltaFrames(@TempDir Path logs) {
        String responseBody = CursorWire.text(
            CursorWire.concat(
                CursorWire.frame(CursorWire.textDelta("Ask <<PII:AWS_")),
                CursorWire.concat(
                    CursorWire.frame(CursorWire.textDelta("KEY:1>> now")),
                    CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
                )
            )
        );
        FakeUpstream upstream = new FakeUpstream(CannedReply.connectProtobuf(responseBody));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            byte[] request = CursorWire.concat(
                CursorWire.frame(CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            );
            String wire = CursorWire.text(send(harness, request, CONNECT_PROTO));
            assertTrue(wire.contains(ProxyHarness.SECRET), wire);
            assertFalse(wire.contains("<<PII:AWS_"), wire);
        } finally {
            harness.stop();
        }
    }

    // A stream that ends while a partial token is still held must still deliver those characters, so
    // the remainder is replayed as one more delta frame ahead of the end-of-stream frame.
    @Test
    @SneakyThrows
    void flushesAnIncompleteProtobufTokenBeforeEndOfStream(@TempDir Path logs) {
        String responseBody = CursorWire.text(
            CursorWire.concat(
                CursorWire.frame(CursorWire.textDelta("trailing <<PII:AWS_")),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            )
        );
        FakeUpstream upstream = new FakeUpstream(CannedReply.connectProtobuf(responseBody));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            byte[] request = CursorWire.concat(
                CursorWire.frame(CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
            );
            String wire = CursorWire.text(send(harness, request, CONNECT_PROTO));
            assertTrue(wire.contains("trailing "), wire);
            assertTrue(wire.contains("<<PII:AWS_"), wire);
            assertEquals(
                1, CursorWire.count(wire, "trailing "),
                "the replay carries the held remainder alone, not a second copy of what was already sent"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void obfuscatesNestedRunInputInsideBidiAppend(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.protobuf());
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            byte[] request = CursorWire.protobuf(
                CursorWire.protobuf("Ask about " + ProxyHarness.KEYWORD)
            );
            int status = sendAppendStatus(harness, request);
            assertEquals(Http.OK, status);
            assertFalse(upstream.lastBody().contains(ProxyHarness.KEYWORD), upstream.lastBody());
            assertTrue(upstream.lastBody().contains("<<PII:COMPANY:1>>"), upstream.lastBody());
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void transformsEveryRequestEnvelopeAndNestedResponseValue(@TempDir Path logs) {
        String nested = "{\"items\":[\"" + TOKEN + "\",1,true,{\"value\":\"" + TOKEN + "\"}]}";
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connect(
                CursorWire.text(CursorWire.frame(nested.getBytes(StandardCharsets.UTF_8)))
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            String tool = "{\"toolResult\":{\"content\":\"" + ProxyHarness.SECRET + "\"}}";
            byte[] body = CursorWire.concat(
                CursorWire.frame(CursorWire.requestJson().getBytes(StandardCharsets.UTF_8)),
                CursorWire.framed(ConnectJsonFrame.END_STREAM, tool)
            );
            String response = new String(send(harness, body), StandardCharsets.UTF_8);
            assertFalse(upstream.lastBody().contains(ProxyHarness.SECRET));
            assertEquals(2, CursorWire.count(upstream.lastBody(), TOKEN), upstream.lastBody());
            assertEquals(2, CursorWire.count(response, ProxyHarness.SECRET), response);
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void passesCursorAuthenticationEndpointsThroughUnchanged(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"ok\":true}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(harness.base().resolve("/auth/login"))
                .header("Authorization", "Bearer cursor-test")
                .POST(HttpRequest.BodyPublishers.ofString(ProxyHarness.SECRET))
                .build();
            int status = sendTextStatus(request);
            assertEquals(Http.OK, status);
            assertEquals(ProxyHarness.SECRET, upstream.lastBody());
            assertEquals("Bearer cursor-test", upstream.lastAuthorization().orElseThrow());
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void rejectsUnsafeConnectRequestsBeforeTheyReachCursor(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.connect(""));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            assertEquals(
                Http.UNSUPPORTED_MEDIA_TYPE,
                status(
                    harness,
                    CursorWire.frame(CursorWire.requestJson().getBytes(StandardCharsets.UTF_8)),
                    ""
                )
            );
            assertEquals(
                Http.BAD_REQUEST,
                status(
                    harness,
                    CursorWire.frame("not json".getBytes(StandardCharsets.UTF_8)),
                    CONNECT_JSON
                )
            );
            assertEquals(
                Http.BAD_REQUEST,
                status(
                    harness,
                    CursorWire.frame("{} {}".getBytes(StandardCharsets.UTF_8)),
                    CONNECT_JSON
                )
            );
            assertEquals(
                Http.UNSUPPORTED_MEDIA_TYPE,
                status(harness, CursorWire.framed(1, CursorWire.requestJson()), CONNECT_JSON)
            );
            assertEquals(Http.BAD_REQUEST, status(harness, new byte[0], CONNECT_JSON));
            assertEquals(0, upstream.requestCount());
        } finally {
            harness.stop();
        }
    }

    // A content type that is present but is neither Connect dialect must be refused. Accepting it and
    // guessing a dialect would mean parsing the stream as something it is not, and the refusal has to
    // say what was wrong rather than return an empty message.
    @Test
    @SneakyThrows
    void refusesAContentTypeThatIsNeitherConnectDialect(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.connect(""));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(harness.base().resolve(RUN))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[0]))
                .build();
            HttpResponse<String> response = sendText(request);
            assertEquals(Http.UNSUPPORTED_MEDIA_TYPE, response.statusCode());
            assertTrue(response.body().contains("connect+json"), response.body());
            assertEquals(0, upstream.requestCount(), "nothing reached Cursor");
        } finally {
            harness.stop();
        }
    }

    // The proxy forwards the method it was asked for, with the upstream's status and headers, and gives
    // the agent a length for a buffered reply. Dropping any of those still yields a plausible response.
    @Test
    @SneakyThrows
    void forwardsTheTargetAndReturnsTheUpstreamStatusAndHeaders(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(
            new CannedReply(
                Http.UNSUPPORTED_MEDIA_TYPE, Map.of("X-Cursor-Note", "kept"), "{}"
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(harness.base().resolve("/auth/login?mode=cli"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            HttpResponse<String> response = sendText(request);
            assertEquals("/auth/login?mode=cli", upstream.lastTarget(), "the target is forwarded intact");
            assertEquals(Http.UNSUPPORTED_MEDIA_TYPE, response.statusCode(), "the status is the upstream's");
            assertEquals("kept", response.headers().firstValue("X-Cursor-Note").orElse(""));
            assertEquals(
                String.valueOf(response.body().length()),
                response.headers().firstValue("Content-Length").orElse(""),
                "a buffered reply carries its length"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void reportsAnUnavailableCursorUpstream(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        upstream.stop();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(harness.base().resolve("/auth/login"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            assertEquals(Http.BAD_GATEWAY, sendTextStatus(request));
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void endsTheUploadCleanlyWhenALaterEnvelopeIsTruncated(@TempDir Path logs) {
        String reply = "{\"text\":\"" + TOKEN + "\"}";
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connect(CursorWire.text(CursorWire.frame(reply.getBytes(StandardCharsets.UTF_8))))
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            byte[] later = CursorWire.framed(ConnectJsonFrame.END_STREAM, "{\"toolResult\":{\"content\":\"cut\"}}");
            byte[] body = CursorWire.concat(
                CursorWire.frame(CursorWire.requestJson().getBytes(StandardCharsets.UTF_8)),
                Arrays.copyOf(later, later.length - 4)
            );
            String response = new String(send(harness, body), StandardCharsets.UTF_8);
            assertEquals(1, CursorWire.count(upstream.lastBody(), TOKEN), upstream.lastBody());
            assertFalse(upstream.lastBody().contains("toolResult"), upstream.lastBody());
            assertTrue(response.contains(ProxyHarness.SECRET), response);
        } finally {
            harness.stop();
        }
    }

    @SneakyThrows
    private static byte[] send(ProxyHarness harness, byte[] body) {
        return send(harness, body, CONNECT_JSON);
    }

    @SneakyThrows
    private static byte[] send(
        ProxyHarness harness, byte[] body, String contentType
    ) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(harness.base().resolve(RUN))
            .header("Content-Type", contentType)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        }
    }

    @SneakyThrows
    private static int status(ProxyHarness harness, byte[] body, String contentType) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(harness.base().resolve(RUN))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (!contentType.isEmpty()) {
            request.header("Content-Type", contentType);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    @SneakyThrows
    private static int sendAppendStatus(ProxyHarness harness, byte[] body) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(harness.base().resolve(APPEND))
            .header("Content-Type", PROTO)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }

    @SneakyThrows
    private static HttpResponse<String> sendText(HttpRequest request) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @SneakyThrows
    private static int sendTextStatus(HttpRequest request) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
        }
    }
}
