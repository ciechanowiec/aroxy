package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives a real {@link ProxyServer} in front of a {@link FakeUpstream} over real sockets and asserts
 * the obfuscation/deobfuscation contract: the upstream sees tokens, the client sees restored values,
 * JSON stays valid across escapes, and an upstream 429 passes through unchanged.
 */
class ProxyServerTest {

    private static final String AWS_KEY = ProxyHarness.SECRET;

    @Test
    @SneakyThrows
    void upstreamReceivesTokensNotTheSecret(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"ok\":true}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"key " + AWS_KEY + " here\"}");
            assertFalse(harness.upstream().lastBody().contains(AWS_KEY), "secret must not leave the machine");
            assertTrue(harness.upstream().lastBody().contains("<<PII:AWS_KEY:"), "a token reached the upstream");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void clientReceivesDeobfuscatedResponse(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"answer\":\"see <<PII:AWS_KEY:1>>\"}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"key " + AWS_KEY + " here\"}");
            HttpResponse<String> response = Http.post(
                harness.base(), Http.MESSAGES,
                "{\"q\":\"key " + AWS_KEY + " again\"}"
            );
            assertTrue(response.body().contains(AWS_KEY), "the response restores the real secret");
            assertFalse(response.body().contains("<<PII:AWS_KEY"), "no token leaks to the client");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void retryAfter429PassesThroughUnchanged(@TempDir Path logs) {
        CannedReply limited = new CannedReply(
            Http.TOO_MANY_REQUESTS,
            Map.of("Content-Type", "application/json", "retry-after", "42"), "{\"type\":\"error\"}"
        );
        FakeUpstream upstream = new FakeUpstream(limited);
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            HttpResponse<String> response = Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"hi\"}");
            assertEquals(Http.TOO_MANY_REQUESTS, response.statusCode(), "the 429 status reaches the client");
            assertEquals("42", response.headers().firstValue("retry-after").orElse(""), "retry-after passes through");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void escapeNextToMatchStaysValidJsonUpstream(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"ok\":true}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            String body = "{\"q\":\"path C:\\\\keys " + AWS_KEY + "\\\" and " + ProxyHarness.KEYWORD + "\"}";
            assertValidJson(body);
            Http.post(harness.base(), Http.MESSAGES, body);
            assertValidJson(harness.upstream().lastBody());
            assertFalse(harness.upstream().lastBody().contains(AWS_KEY), "secret obfuscated even beside an escape");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void nonMessagesPathIsTransparentPassthrough(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"models\":[]}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            HttpResponse<String> response = Http.post(harness.base(), "/v1/models", "{\"q\":\"" + AWS_KEY + "\"}");
            assertEquals(Http.OK, response.statusCode(), "passthrough returns the upstream status");
            assertTrue(harness.upstream().lastBody().contains(AWS_KEY), "passthrough does not obfuscate");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void deadUpstreamYields502JsonError(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"ok\":true}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        upstream.stop();
        try {
            HttpResponse<String> response = Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"hi\"}");
            assertEquals(Http.BAD_GATEWAY, response.statusCode(), "a real upstream failure becomes a 502");
            assertTrue(response.body().contains("proxy_error"), "the 502 body is a small JSON error");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void responseWithoutTokensIsReturnedUnchanged(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"answer\":\"plain text\"}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            HttpResponse<String> response = Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"hi\"}");
            assertEquals("{\"answer\":\"plain text\"}", response.body(), "a token-free body is unchanged");
        } finally {
            harness.stop();
        }
    }

    private static void assertValidJson(String text) {
        JSONObject parsed = new JSONObject(text);
        assertTrue(parsed.has("q") || parsed.has("ok"), "body parses as JSON");
    }
}
