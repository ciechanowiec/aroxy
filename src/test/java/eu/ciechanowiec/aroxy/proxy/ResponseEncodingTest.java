package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * When an upstream answers {@code /v1/messages} with a real {@code content-encoding} (despite the
 * proxy asking for {@code identity}), the body must be forwarded verbatim - never fed as compressed
 * bytes to the deobfuscator - and the encoding header must survive so the client can decode it.
 */
class ResponseEncodingTest {

    private static final String SECRET = ProxyHarness.SECRET;
    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String ENCODED_BODY = "{\"text\":\"" + TOKEN + "\"}";

    @Test
    @SneakyThrows
    void anEncodedResponseIsPassedThroughUntransformed(@TempDir Path logs) {
        CannedReply gzip = new CannedReply(
            Http.OK,
            Map.of("Content-Type", "application/json", "Content-Encoding", "gzip"), ENCODED_BODY
        );
        ProxyHarness harness = new ProxyHarness(new FakeUpstream(gzip), logs);
        try {
            Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"" + SECRET + "\"}");
            HttpResponse<String> response = Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"go\"}");
            assertEquals(ENCODED_BODY, response.body(), "an encoded body is forwarded verbatim, not deobfuscated");
            assertFalse(response.body().contains(SECRET), "the deobfuscator never runs on encoded bytes");
            assertEquals(
                Optional.of("gzip"), response.headers().firstValue("content-encoding"),
                "the content-encoding header is preserved so the client can decode"
            );
            assertTrue(response.body().contains(TOKEN), "the token is left intact rather than reversed");
        } finally {
            harness.stop();
        }
    }
}
