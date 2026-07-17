package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.ciechanowiec.aroxy.config.Settings;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stopping the proxy while a worker is stuck in an in-flight exchange.
 * The drain must fall back to a forced shutdown rather than wait out a request that outlives the grace
 * period, so {@code stop} returns well before the stuck upstream call would have completed.
 */
class ProxyServerConcurrencyTest {

    private static final long UPSTREAM_SLEEP_MILLIS = 3000L;
    private static final long BUSY_WAIT_MILLIS = 400L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    @Test
    @SneakyThrows
    void drainsAStuckWorkerViaForcedShutdown(@TempDir Path logs) {
        HttpServer upstream = slowUpstream();
        ProxyServer server = proxyFor(upstream.getAddress().getPort(), logs);
        server.start();
        try {
            long elapsed = stopWhileAWorkerIsBusy(server);
            assertTrue(elapsed < UPSTREAM_SLEEP_MILLIS, "stop forces a drain instead of waiting the stuck worker out");
        } finally {
            upstream.stop(0);
        }
    }

    @SneakyThrows
    private static long stopWhileAWorkerIsBusy(ProxyServer server) {
        Thread client = new Thread(() -> fireRequest(server.port()));
        client.start();
        Thread.sleep(BUSY_WAIT_MILLIS);
        long start = System.nanoTime();
        server.stop();
        client.join(UPSTREAM_SLEEP_MILLIS);
        return (System.nanoTime() - start) / NANOS_PER_MILLI;
    }

    @SneakyThrows
    private static ProxyServer proxyFor(int upstreamPort, Path logs) {
        Settings settings = new Settings(
            LocalHost.HOST, 0, URI.create("http://" + LocalHost.HOST + ":" + upstreamPort), true
        );
        Obfuscator obfuscator = new Obfuscator(List.of(), new Vault(Optional.empty()));
        ProxyParts parts = new ProxyParts(obfuscator, new Upstream(settings), new AuditLog(logs));
        return new ProxyServer(settings, parts);
    }

    @SneakyThrows
    private static HttpServer slowUpstream() {
        HttpServer server = HttpServer.create(new InetSocketAddress(LocalHost.HOST, 0), 0);
        server.createContext("/", ProxyServerConcurrencyTest::replySlowly);
        server.start();
        return server;
    }

    @SneakyThrows
    private static void replySlowly(HttpExchange exchange) {
        exchange.getRequestBody().readAllBytes();
        sleepQuietly();
        byte[] payload = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(Http.OK, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(UPSTREAM_SLEEP_MILLIS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static void fireRequest(int port) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + LocalHost.HOST + ":" + port + Http.MESSAGES))
            .POST(HttpRequest.BodyPublishers.ofString("{\"q\":\"hi\"}"))
            .build();
        sendQuietly(request);
    }

    private static void sendQuietly(HttpRequest request) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException _) {
            // the proxy may be torn down mid-request, which is expected in this test
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
