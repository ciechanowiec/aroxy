package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the Copilot CONNECT and private TLS listeners through real sockets and a real in-JVM API.
 */
class CopilotProxyTest {

    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String AUTHORIZATION = "Bearer test-copilot-token";
    private static final String RESPONSES = "/responses";
    private static final int HTTPS_PORT = 443;
    private static final int UPGRADE_REQUIRED = 426;

    private final Path base;
    private final FakeUpstream upstream;
    private final ProxyHarness proxy;

    @SneakyThrows
    CopilotProxyTest(@TempDir Path base) {
        this.base = base;
        this.upstream = new FakeUpstream(CannedReply.json("{\"answer\":\"" + TOKEN + "\"}"));
        this.proxy = new ProxyHarness(this.upstream, base, CopilotFixtures.profile());
    }

    @AfterEach
    void stop() {
        this.proxy.stop();
    }

    @Test
    @SneakyThrows
    void obfuscatesAcrossConnectAndRestoresTheReply() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(target("/responses?session=one"))
            .header("Content-Type", "application/json")
            .header("Authorization", AUTHORIZATION)
            .POST(HttpRequest.BodyPublishers.ofString("{\"input\":\"" + ProxyHarness.SECRET + "\"}"))
            .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(Http.OK, response.statusCode(), response.body());
        assertTrue(response.body().contains(ProxyHarness.SECRET), response.body());
        assertTrue(this.upstream.lastBody().contains(TOKEN), this.upstream.lastBody());
        assertFalse(this.upstream.lastBody().contains(ProxyHarness.SECRET), this.upstream.lastBody());
        assertEquals(Optional.of(AUTHORIZATION), this.upstream.lastAuthorization());
        assertEquals("/responses?session=one", this.upstream.lastTarget());
        String audit = Files.readString(this.base.resolve("audit.log"));
        assertTrue(audit.contains("REQUEST  " + RESPONSES), audit);
        assertTrue(audit.contains("deobfuscated 1 token (json)"), audit);
        assertTrue(dump("-redacted-request.json").contains(TOKEN));
        assertTrue(dump("-raw-response.txt").contains(TOKEN));
    }

    @Test
    @SneakyThrows
    void rejectsAnEncodedInferenceBodyBeforeUpstream() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(target(RESPONSES))
            .header("Content-Type", "application/json")
            .header("Content-Encoding", "gzip")
            .POST(HttpRequest.BodyPublishers.ofString("opaque"))
            .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(Http.UNSUPPORTED_MEDIA_TYPE, response.statusCode());
        assertEquals(0, this.upstream.requestCount());
    }

    @Test
    void rejectsAWebSocketUpgradeBeforeUpstream() {
        WebSocket.Listener listener = new WebSocket.Listener() {
        };
        RuntimeException failure = assertThrows(
            RuntimeException.class,
            () -> {
                try (HttpClient http = CopilotFixtures.client(this.proxy.base(), this.base)) {
                    http.newWebSocketBuilder().buildAsync(webSocketTarget(), listener).join();
                }
            }
        );
        WebSocketHandshakeException handshake = (WebSocketHandshakeException) Optional
            .ofNullable(failure.getCause()).orElseThrow();
        assertEquals(UPGRADE_REQUIRED, handshake.getResponse().statusCode());
        assertEquals(0, this.upstream.requestCount());
    }

    @Test
    @SneakyThrows
    void forwardsAnEmptyInferenceRequestAndFormatsTheDefaultAuthority() {
        HttpRequest request = HttpRequest.newBuilder().uri(target(RESPONSES)).GET().build();
        assertEquals(Http.OK, send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(
            "api.githubcopilot.com", CopilotRouter.authority("api.githubcopilot.com", HTTPS_PORT)
        );
    }

    @Test
    @SneakyThrows
    void rejectsMalformedJsonAndReportsAnUnavailableUpstream() {
        HttpResponse<String> malformed = send(
            request(target(RESPONSES), "{", Map.of()), HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(Http.BAD_REQUEST, malformed.statusCode());
        URI destination = target(RESPONSES);
        this.upstream.stop();
        HttpResponse<String> failed = send(
            request(destination, "{}", Map.of()), HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(Http.BAD_GATEWAY, failed.statusCode());
    }

    private static HttpRequest request(URI destination, String body, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(destination)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return builder.build();
    }

    @SneakyThrows
    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try (HttpClient http = CopilotFixtures.client(this.proxy.base(), this.base)) {
            return http.send(request, handler);
        }
    }

    private URI target(String path) {
        return URI.create("https://localhost:" + this.upstream.uri().getPort() + path);
    }

    private URI webSocketTarget() {
        return URI.create("wss://localhost:" + this.upstream.uri().getPort() + RESPONSES);
    }

    @SneakyThrows
    private String dump(String suffix) {
        try (Stream<Path> files = Files.list(this.base.resolve("wire"))) {
            Path selected = files.filter(path -> path.getFileName().toString().endsWith(suffix))
                .findFirst().orElseThrow();
            return Files.readString(selected);
        }
    }
}
