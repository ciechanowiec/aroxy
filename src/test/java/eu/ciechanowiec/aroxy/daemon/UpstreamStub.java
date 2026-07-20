package eu.ciechanowiec.aroxy.daemon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A real in-JVM upstream standing in for a client's API. It records the last request body and path it
 * received (so a test can assert the proxy sent a token rather than the secret, and sent it to the
 * right place) and returns a fixed JSON reply.
 */
final class UpstreamStub {

    private static final int OK = 200;
    private static final String REPLY = "{\"ok\":true}";
    private final HttpServer server;
    private final AtomicReference<String> lastBody;
    private final AtomicReference<String> lastPath;

    UpstreamStub() throws IOException {
        this.lastBody = new AtomicReference<>("");
        this.lastPath = new AtomicReference<>("");
        InetAddress local = InetAddress.getLoopbackAddress();
        this.server = HttpServer.create(new InetSocketAddress(local, 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    URI uri() {
        return URI.create(
            "http://" + InetAddress.getLoopbackAddress().getHostAddress()
                + ":" + this.server.getAddress().getPort()
        );
    }

    String lastBody() {
        return Objects.requireNonNull(this.lastBody.get());
    }

    String lastPath() {
        return Objects.requireNonNull(this.lastPath.get());
    }

    void stop() {
        this.server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        this.lastBody.set(new String(body, StandardCharsets.UTF_8));
        this.lastPath.set(exchange.getRequestURI().getRawPath());
        byte[] payload = REPLY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(OK, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
        exchange.close();
    }
}
