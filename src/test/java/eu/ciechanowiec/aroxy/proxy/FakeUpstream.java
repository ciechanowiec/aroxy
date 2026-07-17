package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A real in-JVM upstream that records the last request body it received and returns a canned reply.
 * It stands in for the Claude API so the proxy can be driven end to end over real sockets.
 */
final class FakeUpstream {

    private final HttpServer server;
    private final AtomicReference<String> lastBody;
    private final CannedReply reply;

    FakeUpstream(CannedReply reply) throws IOException {
        this.reply = reply;
        this.lastBody = new AtomicReference<>("");
        this.server = HttpServer.create(new InetSocketAddress(LocalHost.HOST, 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    URI uri() {
        return URI.create("http://" + LocalHost.HOST + ":" + this.server.getAddress().getPort());
    }

    String lastBody() {
        return Objects.requireNonNull(this.lastBody.get());
    }

    void stop() {
        this.server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        this.lastBody.set(new String(body, StandardCharsets.UTF_8));
        this.reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        byte[] payload = this.reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(this.reply.status(), payload.length == 0 ? -1 : payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
        exchange.close();
    }
}
