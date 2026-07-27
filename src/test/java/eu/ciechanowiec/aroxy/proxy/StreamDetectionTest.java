package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whether a reply is streamed through the transformer or buffered whole is decided twice: by its
 * content type, and, when that says nothing, by a look at the first bytes of the body. Getting it
 * wrong is not visibly broken - both routes deobfuscate - but a stream read as a buffer arrives only
 * once the upstream has finished, and a buffer read as a stream is reassembled by the wrong reader.
 * The audit line records which route ran, so it is what these assert on.
 */
class StreamDetectionTest {

    private static final String SECRET = ProxyHarness.SECRET;
    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String EVENT_STREAM = "text/event-stream";
    private static final String JSON = "application/json";
    private static final String STREAMED = "(stream)";
    private static final String BUFFERED = "(json)";

    // The declared content type is authoritative. A body that happens not to look like an event stream
    // does not turn a declared stream into a buffered reply.
    @Test
    @SneakyThrows
    void trustsTheDeclaredContentTypeOverTheBodyShape(@TempDir Path logs) {
        String route = route(logs, EVENT_STREAM, "{\"text\":\"" + TOKEN + "\"}");
        assertEquals(STREAMED, route, "a declared event stream is streamed whatever its first bytes are");
    }

    // With no such declaration the first bytes decide, so an upstream that mislabels a real event
    // stream as JSON is still drained incrementally rather than held until it closes.
    @Test
    @SneakyThrows
    void infersAStreamFromTheFirstBytesWhenTheTypeSaysNothing(@TempDir Path logs) {
        String body = "data: {\"type\":\"content_block_delta\",\"delta\":"
            + "{\"type\":\"text_delta\",\"text\":\"" + TOKEN + "\"}}\n\n";
        assertEquals(STREAMED, route(logs, JSON, body), "an unlabelled event stream is recognised");
    }

    @Test
    @SneakyThrows
    void readsAnOrdinaryJsonReplyAsABuffer(@TempDir Path logs) {
        assertEquals(
            BUFFERED, route(logs, JSON, "{\"text\":\"" + TOKEN + "\"}"),
            "a plain JSON reply is not mistaken for a stream"
        );
    }

    // The probe walks past leading whitespace to find the first meaningful character. A body that is
    // nothing but whitespace has no such character, and running off the end of it is not an option.
    @Test
    @SneakyThrows
    void handlesABodyThatIsEntirelyWhitespace(@TempDir Path logs) {
        assertEquals(BUFFERED, route(logs, JSON, "   \n\t  "), "whitespace alone is not an event stream");
    }

    @Test
    @SneakyThrows
    void handlesAnEmptyBody(@TempDir Path logs) {
        assertEquals(BUFFERED, route(logs, JSON, ""), "there is nothing to probe, so nothing is inferred");
    }

    // identity is the encoding the proxy itself asks for, so it means "not encoded". Treating it as a
    // real encoding would pass every reply through untransformed and silently stop deobfuscating.
    @Test
    @SneakyThrows
    void treatsIdentityAsNoEncodingAtAll(@TempDir Path logs) {
        CannedReply reply = new CannedReply(
            Http.OK, Map.of("Content-Type", JSON, "Content-Encoding", "identity"),
            "{\"text\":\"" + TOKEN + "\"}"
        );
        ProxyHarness harness = new ProxyHarness(new FakeUpstream(reply), logs);
        try {
            Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"" + SECRET + "\"}");
            HttpResponse<String> response = Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"go\"}");
            assertTrue(
                response.body().contains(SECRET),
                "an identity-encoded reply is still deobfuscated: " + response.body()
            );
        } finally {
            harness.stop();
        }
    }

    /**
     * Drives one reply through the proxy and reports which route the audit log says was taken.
     */
    @SneakyThrows
    private static String route(Path logs, String contentType, String body) {
        CannedReply reply = new CannedReply(Http.OK, Map.of("Content-Type", contentType), body);
        ProxyHarness harness = new ProxyHarness(new FakeUpstream(reply), logs);
        try {
            Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"" + SECRET + "\"}");
        } finally {
            harness.stop();
        }
        String audit = Files.readString(logs.resolve("audit.log"));
        return audit.contains(STREAMED) ? STREAMED : BUFFERED;
    }
}
