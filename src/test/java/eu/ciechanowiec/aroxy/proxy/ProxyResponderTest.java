package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Edge cases in writing the upstream reply back to the client.
 * An empty body is returned with no content, and an {@code identity} content-encoding is treated as
 * no encoding so the body is still deobfuscated rather than passed through verbatim.
 */
class ProxyResponderTest {

    private static final String SECRET = ProxyHarness.SECRET;
    private static final String TOKEN = "<<PII:AWS_KEY:1>>";

    @Test
    @SneakyThrows
    void anEmptyMessagesBodyIsReturnedEmpty(@TempDir Path logs) {
        ProxyHarness harness = new ProxyHarness(new FakeUpstream(CannedReply.json("")), logs);
        try {
            HttpResponse<String> response = Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"hi\"}");
            assertEquals(Http.OK, response.statusCode(), "an empty upstream body still yields a 200");
            assertEquals("", response.body(), "an empty body is written without content");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void anIdentityEncodedResponseIsStillDeobfuscated(@TempDir Path logs) {
        CannedReply identity = new CannedReply(
            Http.OK,
            Map.of("Content-Type", "application/json", "Content-Encoding", "identity"),
            "{\"a\":\"" + TOKEN + "\"}"
        );
        ProxyHarness harness = new ProxyHarness(new FakeUpstream(identity), logs);
        try {
            HttpResponse<String> response = Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"" + SECRET + "\"}");
            assertTrue(response.body().contains(SECRET), "an identity encoding does not block deobfuscation");
            assertTrue(
                response.headers().firstValue("content-encoding").isEmpty(),
                "identity is not echoed to the client as a real encoding"
            );
        } finally {
            harness.stop();
        }
    }
}
