package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit-tests the small, pure proxy helpers: the benign-disconnect classifier, the SSE field locator,
 * the SSE event predicates, and the restricted/response header rules. These cover the branches that
 * the socket-level tests do not naturally reach.
 */
class ProxyUnitsTest {

    @Test
    void brokenPipeAndResetAreBenign() {
        assertTrue(ClientDisconnect.isBenign(new IOException("Broken pipe")), "broken pipe is benign");
        assertTrue(ClientDisconnect.isBenign(new IOException("Connection reset by peer")), "reset is benign");
        assertTrue(ClientDisconnect.isBenign(new IOException("connection closed")), "closed is benign");
        assertTrue(ClientDisconnect.isBenign(new IOException("stream was reset: CANCEL")), "stream reset benign");
    }

    @Test
    void eofTypeIsBenignAndOtherFailuresAreNot() {
        assertTrue(ClientDisconnect.isBenign(new EofException()), "an EofException-typed failure is benign");
        assertFalse(ClientDisconnect.isBenign(new IOException("connection refused")), "refused is a real failure");
        assertFalse(ClientDisconnect.isBenign(new IOException("")), "a non-disconnect message is not benign");
    }

    @Test
    void sseFieldLocatesTextAndPartialJson() {
        String text = "data: {\"delta\":{\"type\":\"text_delta\",\"text\":\"hi <<PII:X:1>>\"}}";
        assertEquals("hi <<PII:X:1>>", SseField.locate(text).orElseThrow().value(text), "text value located");
        String json = "data: {\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"a\\\"b\"}}";
        assertEquals("a\\\"b", SseField.locate(json).orElseThrow().value(json), "partial_json value located");
    }

    @Test
    void sseFieldAbsentWhenNoDeltaValue() {
        assertTrue(SseField.locate("data: {\"type\":\"ping\"}").isEmpty(), "no delta field present");
    }

    @Test
    void sseEventClassifiesDeltaTerminalAndIndex() {
        SseEvent delta = new SseEvent(
            "{\"type\":\"content_block_delta\",\"index\":2,"
                + "\"delta\":{\"type\":\"text_delta\"}}"
        );
        assertTrue(delta.isTextDelta(), "a text_delta is a delta event");
        assertEquals(2, delta.index().orElseThrow(), "the index is read");
        assertTrue(new SseEvent("{\"type\":\"message_stop\"}").isTerminal(), "message_stop is terminal");
        assertFalse(new SseEvent("{\"type\":\"ping\"}").isTerminal(), "ping is not terminal");
        assertTrue(new SseEvent("{\"type\":\"ping\"}").index().isEmpty(), "no index in a ping event");
    }

    @Test
    void restrictedAndCopyableHeaderRules() {
        assertTrue(RestrictedHeaders.isForbidden("Host"), "host is restricted");
        assertTrue(RestrictedHeaders.isForbidden("Proxy-Authorization"), "proxy-* is restricted");
        assertFalse(RestrictedHeaders.isForbidden("x-api-key"), "auth headers are forwarded");
    }

    @Test
    void obfuscateAndDeobfuscateMatchTheSameMessagesPathSegments() {
        assertTrue(request("/v1/messages", Map.of()).shouldObfuscate(), "the exact path is obfuscated");
        assertTrue(request("/v1/messages/count_tokens", Map.of()).shouldObfuscate(), "a sub-resource is obfuscated");
        assertTrue(
            request("/v1/messages/count_tokens", Map.of()).shouldDeobfuscate(),
            "and the same sub-resource is deobfuscated, so no token survives into the client"
        );
        assertFalse(request("/v1/messagesX", Map.of()).shouldObfuscate(), "a near-miss is a passthrough");
        assertFalse(request("/v1/messagesX", Map.of()).shouldDeobfuscate(), "a near-miss is a passthrough");
    }

    @Test
    void aContentEncodedRequestBodyIsNotDecodedAsText() {
        Map<String, List<String>> gzip = Map.of("Content-Encoding", List.of("gzip"));
        assertFalse(
            request("/v1/messages", gzip).shouldObfuscate(),
            "a compressed body is passed through instead of being mangled by a UTF-8 round trip"
        );
        Map<String, List<String>> identity = Map.of("Content-Encoding", List.of("identity"));
        assertTrue(request("/v1/messages", identity).shouldObfuscate(), "an identity-encoded body is still obfuscated");
    }

    private static ProxyRequest request(String path, Map<String, List<String>> headers) {
        return new ProxyRequest("POST", path, path, headers, "{\"q\":\"x\"}".getBytes(StandardCharsets.UTF_8));
    }
}
