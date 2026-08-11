package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @TempDir
    private Path base;
    private FakeUpstream upstream;
    private ProxyHarness proxy;

    @BeforeEach
    @SneakyThrows
    void start() {
        this.upstream = new FakeUpstream(CannedReply.json("{}"));
        this.proxy = new ProxyHarness(this.upstream, this.base, CopilotFixtures.profile());
    }

    @AfterEach
    void stop() {
        this.proxy.stop();
    }

    @Test
    @SneakyThrows
    void restoresResponsesAndChatCompletionStreams() {
        restart(CannedReply.sse(responseEvent(TOKEN) + "data: {\"type\":\"response.completed\"}\n\n"));
        assertTrue(post(RESPONSES, SECRET_BODY).body().contains(ProxyHarness.SECRET));
        restart(CannedReply.sse(chatEvent(TOKEN) + "data: [DONE]\n\n"));
        assertTrue(post("/chat/completions", SECRET_BODY).body().contains(ProxyHarness.SECRET));
        String audit = Files.readString(this.base.resolve("audit.log"));
        List<Path> dumps = wireFiles();
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
        assertTrue(this.upstream.lastBody().contains(ProxyHarness.SECRET), this.upstream.lastBody());
        restart(new CannedReply(Http.OK, Map.of("Content-Encoding", "gzip"), "opaque"));
        HttpResponse<byte[]> encoded = send(
            request(RESPONSES, "{}"), HttpResponse.BodyHandlers.ofByteArray()
        );
        assertEquals("gzip", encoded.headers().firstValue("Content-Encoding").orElseThrow());
        assertEquals("opaque", new String(encoded.body(), StandardCharsets.UTF_8));
    }

    private void restart(CannedReply reply) {
        this.proxy.stop();
        this.upstream = new FakeUpstream(reply);
        try {
            this.proxy = new ProxyHarness(this.upstream, this.base, CopilotFixtures.profile());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
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
        try (HttpClient http = client()) {
            return http.send(request, handler);
        }
    }

    @SneakyThrows
    private HttpClient client() {
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null);
        trust.setCertificateEntry("aroxy", authority());
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, managers.getTrustManagers(), null);
        InetSocketAddress address = new InetSocketAddress(this.proxy.base().getHost(), this.proxy.base().getPort());
        return HttpClient.newBuilder().sslContext(context).proxy(ProxySelector.of(address)).build();
    }

    @SneakyThrows
    private Certificate authority() {
        try (InputStream input = Files.newInputStream(this.base.resolve("copilot-ca.pem"))) {
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    @SneakyThrows
    private List<Path> wireFiles() {
        try (Stream<Path> files = Files.list(this.base.resolve("wire"))) {
            return files.filter(path -> path.getFileName().toString().endsWith("-raw-response.txt")).toList();
        }
    }

    private URI target(String path) {
        return URI.create("https://localhost:" + this.upstream.uri().getPort() + path);
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
