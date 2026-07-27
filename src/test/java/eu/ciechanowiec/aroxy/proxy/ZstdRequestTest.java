package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CodexCli;
import io.airlift.compress.zstd.ZstdOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Codex CLI compresses its request bodies with zstd by default when signed in with ChatGPT. A
 * compressed body carries no text for the rules to match, so without inflating it the proxy would
 * report itself healthy while forwarding the user's secrets untouched. These tests pin that it is
 * inflated, obfuscated, and forwarded as plain bytes.
 */
class ZstdRequestTest {

    private static final String SECRET = ProxyHarness.SECRET;
    private static final String EMPTY_JSON = "{}";
    private static final String CONTENT_ENCODING = "Content-Encoding";
    private static final int OK = 200;
    private static final int UNSUPPORTED_MEDIA_TYPE = 415;

    @SneakyThrows
    private static byte[] compressed(String text) {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (OutputStream out = new ZstdOutputStream(sink)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return sink.toByteArray();
    }

    @SneakyThrows
    private static HttpResponse<String> post(URI base, byte[] body, Map<String, String> headers) {
        return post(base, Http.RESPONSES, body, headers);
    }

    @SneakyThrows
    private static HttpResponse<String> post(
        URI base, String path, byte[] body, Map<String, String> headers
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(builder::header);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    @SneakyThrows
    void aZstdRequestIsInflatedObfuscatedAndForwardedAsPlainBytes(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json(EMPTY_JSON));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CodexCli.profile());
        try {
            byte[] body = compressed("{\"input\":\"" + SECRET + "\"}");
            HttpResponse<String> response = post(harness.base(), body, Map.of(CONTENT_ENCODING, "zstd"));
            assertEquals(OK, response.statusCode(), response.body());
            assertFalse(upstream.lastBody().contains(SECRET), "the secret must not leave the machine");
            assertTrue(upstream.lastBody().contains("<<PII:AWS_KEY:"), "a token reached the upstream instead");
            assertTrue(new JSONObject(upstream.lastBody()).has("input"), "the forwarded body is valid JSON");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void theForwardedRequestNoLongerClaimsToBeCompressed(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json(EMPTY_JSON));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CodexCli.profile());
        try {
            byte[] body = compressed("{\"input\":\"hello\"}");
            post(harness.base(), body, Map.of(CONTENT_ENCODING, "zstd"));
            assertTrue(
                upstream.lastContentEncoding().isEmpty(),
                "the header is dropped with the compression; leaving it would announce zstd over "
                    + "plain bytes and the upstream would reject the request"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void aMalformedZstdBodyIsRejectedWithoutReachingTheUpstream(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json(EMPTY_JSON));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CodexCli.profile());
        try {
            byte[] garbage = "not a zstd frame at all".getBytes(StandardCharsets.UTF_8);
            HttpResponse<String> response = post(harness.base(), garbage, Map.of(CONTENT_ENCODING, "zstd"));
            assertEquals(UNSUPPORTED_MEDIA_TYPE, response.statusCode(), response.body());
            assertTrue(response.body().contains("unsupported_encoding"), response.body());
            assertEquals(0, upstream.requestCount(), "an unreadable body must never leave the machine");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void anUnsupportedEncodingOnAnInScopeBodyIsRejectedLocally(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json(EMPTY_JSON));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CodexCli.profile());
        try {
            byte[] body = ("{\"input\":\"" + SECRET + "\"}").getBytes(StandardCharsets.UTF_8);
            HttpResponse<String> response = post(harness.base(), body, Map.of(CONTENT_ENCODING, "gzip"));
            assertEquals(UNSUPPORTED_MEDIA_TYPE, response.statusCode(), response.body());
            assertEquals(0, upstream.requestCount(), "opaque in-scope bytes must never be forwarded");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void anEncodedOutOfScopeBodyRemainsATransparentPassthrough(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json(EMPTY_JSON));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CodexCli.profile());
        try {
            byte[] body = "opaque model-list body".getBytes(StandardCharsets.UTF_8);
            HttpResponse<String> response = post(
                harness.base(), "/models", body, Map.of(CONTENT_ENCODING, "gzip")
            );
            assertEquals(OK, response.statusCode(), response.body());
            assertEquals(1, upstream.requestCount(), "out-of-scope traffic retains passthrough semantics");
            assertEquals("opaque model-list body", upstream.lastBody());
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void anUncompressedRequestIsUntouchedByTheDecoder(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json(EMPTY_JSON));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CodexCli.profile());
        try {
            byte[] body = ("{\"input\":\"" + SECRET + "\"}").getBytes(StandardCharsets.UTF_8);
            post(harness.base(), body, Map.of(CONTENT_ENCODING, "identity"));
            assertTrue(
                upstream.lastBody().contains("<<PII:AWS_KEY:"),
                "an identity-encoded body needs no inflating and is obfuscated as usual"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    void aZstdFrameRoundTripsThroughTheProductionDecoder() {
        byte[] original = ("{\"input\":\"" + SECRET + "\"}").getBytes(StandardCharsets.UTF_8);
        byte[] inflated = ZstdBody.inflated(compressed(new String(original, StandardCharsets.UTF_8))).orElseThrow();
        assertArrayEquals(original, inflated, "what the decoder returns is what was compressed");
    }

    @Test
    void bytesThatAreNotAFrameInflateToNothingRatherThanThrowing() {
        assertTrue(
            ZstdBody.inflated("nonsense".getBytes(StandardCharsets.UTF_8)).isEmpty(),
            "the caller sees decoding failed and rejects an in-scope request"
        );
        assertTrue(ZstdBody.inflated(new byte[0]).isEmpty(), "an empty body is not a frame either");
    }

    @Test
    void anEncodingThisProxyCannotUndoIsLeftAlone() {
        ProxyRequest gzipped = new ProxyRequest(
            "POST", Http.RESPONSES, Http.RESPONSES,
            Map.of(CONTENT_ENCODING, List.of("gzip")), compressed(EMPTY_JSON)
        );
        assertEquals(
            gzipped, gzipped.decoded(),
            "only an encoding the proxy can undo is undone; the rest keeps its header and its bytes"
        );
    }

    // Inflating a body rebuilds the request. Everything except the now-false content-encoding has to
    // survive that rebuild: the authentication header the upstream needs, and the query the path
    // carries. Dropping either would be invisible in the body yet fatal to the request.
    @Test
    @SneakyThrows
    void inflationKeepsEveryOtherHeaderAndTheQuery(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"ok\":true}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(harness.base().resolve(Http.MESSAGES + "?beta=true"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer keep-me")
                .header("Content-Encoding", "zstd")
                .POST(HttpRequest.BodyPublishers.ofByteArray(compressed("{\"q\":\"hi\"}")))
                .build();
            discard(request);
            assertEquals(
                "Bearer keep-me", upstream.lastAuthorization().orElse(""),
                "the authentication header survives the rebuild"
            );
            assertEquals(
                Http.MESSAGES + "?beta=true", upstream.lastTarget(),
                "and so does the query string"
            );
            assertTrue(
                upstream.lastContentEncoding().isEmpty(),
                "while the encoding the proxy undid is gone"
            );
        } finally {
            harness.stop();
        }
    }

    @SneakyThrows
    private static void discard(HttpRequest request) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        }
    }
}
