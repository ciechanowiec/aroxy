package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies Copilot response transformation, metadata, and proof artifacts over real TLS sockets.
 */
class CopilotProxyResponseTest {

    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String TOKEN_HEAD = "<<PII:AWS_";
    private static final String RESPONSES = "/responses";
    private static final String SECRET_BODY = "{\"input\":\"" + ProxyHarness.SECRET + "\"}";
    private static final String ANSWER = "{\"answer\":\"" + TOKEN + "\"}";
    private static final int ACCEPTED = 202;

    private final Path base;

    /**
     * The upstream and the proxy in front of it, held together in one reference because every case here
     * restages them with a different canned reply. Pairing them is what keeps a test from reading the
     * port of one upstream while talking to the proxy of another.
     */
    private final AtomicReference<ProxyHarness> proxy;

    @SneakyThrows
    CopilotProxyResponseTest(@TempDir Path base) {
        this.base = base;
        this.proxy = new AtomicReference<>(staged(CannedReply.json("{}"), base));
    }

    @AfterEach
    void stop() {
        this.proxy().stop();
    }

    @Test
    @SneakyThrows
    void restoresResponsesAndChatCompletionStreams() {
        restart(CannedReply.sse(responseEvent(TOKEN) + "data: {\"type\":\"response.completed\"}\n\n"));
        assertTrue(post(RESPONSES, SECRET_BODY).body().contains(ProxyHarness.SECRET));
        restart(CannedReply.sse(chatEvent(TOKEN) + "data: [DONE]\n\n"));
        assertTrue(post("/chat/completions", SECRET_BODY).body().contains(ProxyHarness.SECRET));
        String audit = Files.readString(this.base.resolve("audit.log"));
        List<Path> dumps = CopilotFixtures.wireFiles(this.base);
        assertEquals(2, audit.split("token \\(stream\\)", -1).length - 1, audit);
        assertEquals(2, dumps.size());
        assertEquals(2, dumps.stream().filter(path -> read(path).contains(TOKEN)).count());
    }

    @Test
    @SneakyThrows
    void preservesStreamStatusAndHeadersAndFlushesHeldText() {
        restart(
            new CannedReply(
                ACCEPTED,
                Map.of("Content-Type", "text/event-stream", "X-Copilot-Test", "present"),
                chatEvent(TOKEN_HEAD)
            )
        );
        HttpResponse<String> response = post("/chat/completions", SECRET_BODY);
        assertEquals(ACCEPTED, response.statusCode());
        assertEquals("present", response.headers().firstValue("X-Copilot-Test").orElseThrow());
        assertTrue(response.body().contains(TOKEN_HEAD), response.body());
    }

    @Test
    @SneakyThrows
    void flushesHeldTextFromTheResponsesDialect() {
        restart(CannedReply.sse(responseEvent(TOKEN_HEAD)));
        HttpResponse<String> response = post(RESPONSES, SECRET_BODY);
        assertTrue(response.body().contains(TOKEN_HEAD), response.body());
    }

    @Test
    @SneakyThrows
    void preservesBufferedAndPassthroughResponseMetadata() {
        Map<String, String> headers = Map.of(
            "Content-Type", "application/json", "X-Copilot-Test", "present"
        );
        restart(new CannedReply(ACCEPTED, headers, ANSWER));
        HttpResponse<String> buffered = post(RESPONSES, SECRET_BODY);
        assertEquals(ACCEPTED, buffered.statusCode());
        assertEquals("present", buffered.headers().firstValue("X-Copilot-Test").orElseThrow());
        restart(new CannedReply(ACCEPTED, headers, ANSWER));
        HttpResponse<String> passthrough = post("/models", SECRET_BODY);
        assertEquals(ACCEPTED, passthrough.statusCode());
        assertEquals("present", passthrough.headers().firstValue("X-Copilot-Test").orElseThrow());
    }

    @Test
    @SneakyThrows
    void treatsIdentityEncodingAsTransformable() {
        restart(
            new CannedReply(
                Http.OK, Map.of("Content-Type", "application/json", "Content-Encoding", "identity"), ANSWER
            )
        );
        HttpResponse<String> response = post(RESPONSES, SECRET_BODY);
        assertTrue(response.body().contains(ProxyHarness.SECRET), response.body());
    }

    @Test
    @SneakyThrows
    void passesControlRoutesAndEncodedRepliesWithoutTransformation() {
        restart(CannedReply.json("{\"value\":\"" + TOKEN + "\"}"));
        HttpResponse<String> control = post("/models", ProxyHarness.SECRET);
        assertTrue(control.body().contains(TOKEN), control.body());
        String forwarded = this.proxy().upstream().lastBody();
        assertTrue(forwarded.contains(ProxyHarness.SECRET), forwarded);
        restart(new CannedReply(Http.OK, Map.of("Content-Encoding", "gzip"), "opaque"));
        HttpResponse<byte[]> encoded = send(
            request(RESPONSES, "{}"), HttpResponse.BodyHandlers.ofByteArray()
        );
        assertEquals("gzip", encoded.headers().firstValue("Content-Encoding").orElseThrow());
        assertEquals("opaque", new String(encoded.body(), StandardCharsets.UTF_8));
    }

    private void restart(CannedReply reply) {
        this.proxy().stop();
        this.proxy.set(staged(reply, this.base));
    }

    private static ProxyHarness staged(CannedReply reply, Path base) {
        try {
            return new ProxyHarness(new FakeUpstream(reply), base, CopilotFixtures.profile());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private ProxyHarness proxy() {
        return Objects.requireNonNull(this.proxy.get());
    }

    @SneakyThrows
    private HttpResponse<String> post(String path, String body) {
        return send(request(path, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String path, String body) {
        return HttpRequest.newBuilder().uri(target(path)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    @SneakyThrows
    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try (HttpClient http = CopilotFixtures.client(this.proxy().base(), this.base)) {
            return http.send(request, handler);
        }
    }

    private URI target(String path) {
        return URI.create("https://localhost:" + this.proxy().upstream().uri().getPort() + path);
    }

    private static String responseEvent(String value) {
        return "data: {\"type\":\"response.output_text.delta\",\"delta\":\"" + value + "\"}\n\n";
    }

    private static String chatEvent(String value) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + value + "\"}}]}\n\n";
    }

    @SneakyThrows
    private static String read(Path path) {
        return Files.readString(path);
    }
}
