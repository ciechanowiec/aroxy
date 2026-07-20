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
 * the request's body and encoding predicates, and the restricted/response header rules. These cover
 * the branches that the socket-level tests do not naturally reach. Which paths a listener transforms
 * is a property of the client rather than of the proxy, and is covered by {@code PathScopeTest}.
 */
class ProxyUnitsTest {

    private static final String TEXT_KEY = "\"text\":\"";
    private static final String PATH = "/v1/messages";

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
    void sseFieldLocatesTheValueOfTheKeyItIsGiven() {
        String text = "data: {\"delta\":{\"type\":\"text_delta\",\"text\":\"hi <<PII:X:1>>\"}}";
        assertEquals(
            "hi <<PII:X:1>>", SseField.locate(text, TEXT_KEY).orElseThrow().value(text), "text value located"
        );
        String json = "data: {\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"a\\\"b\"}}";
        assertEquals(
            "a\\\"b", SseField.locate(json, "\"partial_json\":\"").orElseThrow().value(json),
            "the value ends at the first unescaped quote, so an escaped one does not truncate it"
        );
    }

    @Test
    void sseFieldAbsentWhenTheKeyIsNotPresent() {
        assertTrue(SseField.locate("data: {\"type\":\"ping\"}", TEXT_KEY).isEmpty(), "no such field present");
    }

    @Test
    void sseFieldAbsentWhenTheValueIsNeverClosed() {
        String truncated = "data: {\"text\":\"unterminated";
        assertTrue(
            SseField.locate(truncated, TEXT_KEY).isEmpty(),
            "a value with no closing quote spans no locatable field, so a partial event is left alone"
        );
    }

    @Test
    void restrictedAndCopyableHeaderRules() {
        assertTrue(RestrictedHeaders.isForbidden("Host"), "host is restricted");
        assertTrue(RestrictedHeaders.isForbidden("Proxy-Authorization"), "proxy-* is restricted");
        assertFalse(RestrictedHeaders.isForbidden("x-api-key"), "auth headers are forwarded");
    }

    @Test
    void aContentEncodedRequestBodyIsRecognisedAsEncoded() {
        Map<String, List<String>> gzip = Map.of("Content-Encoding", List.of("gzip"));
        assertTrue(request(gzip).isEncoded(), "a compressed body is flagged rather than decoded");
        assertEquals("gzip", request(gzip).encoding().orElseThrow(), "the encoding is named");
    }

    @Test
    void anIdentityOrBlankOrAbsentEncodingIsNotEncoded() {
        Map<String, List<String>> identity = Map.of("Content-Encoding", List.of("identity"));
        assertFalse(request(identity).isEncoded(), "identity is not an encoding to undo");
        Map<String, List<String>> blank = Map.of("Content-Encoding", List.of("  "));
        assertFalse(request(blank).isEncoded(), "a blank header names no encoding");
        assertFalse(request(Map.of()).isEncoded(), "an absent header names no encoding");
    }

    @Test
    void anEmptyBodyIsDistinguishedFromOneWithContent() {
        assertTrue(request(Map.of()).hasBody(), "a body with content is reported");
        ProxyRequest empty = new ProxyRequest("POST", PATH, PATH, Map.of(), new byte[0]);
        assertFalse(empty.hasBody(), "an empty body is reported as none, so there is nothing to obfuscate");
    }

    private static ProxyRequest request(Map<String, List<String>> headers) {
        return new ProxyRequest("POST", PATH, PATH, headers, "{\"q\":\"x\"}".getBytes(StandardCharsets.UTF_8));
    }
}
