package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import eu.ciechanowiec.aroxy.client.Gateway;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the upstream client does not follow redirects: a 3xx from the upstream is passed
 * straight back rather than chased, so the forwarded authentication headers are never re-sent to a
 * redirect target on another host.
 */
class UpstreamRedirectTest {

    private static final int FOUND = 302;
    private static final int OK = 200;
    private static final int NO_BODY = -1;
    private static final String LOOPBACK = InetAddress.getLoopbackAddress().getHostAddress();

    @Test
    @SneakyThrows
    void doesNotFollowRedirectToAnotherHost() {
        AtomicBoolean targetHit = new AtomicBoolean(false);
        HttpServer target = start(
            exchange -> {
                targetHit.set(true);
                reply(exchange, OK);
            }
        );
        HttpServer redirector = start(exchange -> redirect(exchange, target.getAddress().getPort()));
        try {
            UpstreamReply reply = send(redirector.getAddress().getPort());
            assertEquals(FOUND, reply.status());
            assertFalse(targetHit.get(), "the redirect target must never be reached");
        } finally {
            target.stop(0);
            redirector.stop(0);
        }
    }

    @SneakyThrows
    private static UpstreamReply send(int port) {
        Gateway gateway = new Gateway(0, URI.create("http://" + LOOPBACK + ":" + port));
        UpstreamReply reply = new Upstream(gateway).send(
            "GET", "/v1/messages",
            Map.of("authorization", List.of("Bearer secret")), new byte[0]
        );
        try (InputStream body = reply.body()) {
            body.readAllBytes();
        }
        return reply;
    }

    @SneakyThrows
    private static void redirect(HttpExchange exchange, int targetPort) {
        exchange.getResponseHeaders().add("Location", "http://" + LOOPBACK + ":" + targetPort + "/moved");
        reply(exchange, FOUND);
    }

    @SneakyThrows
    private static HttpServer start(HttpHandler handler) {
        HttpServer server = HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    @SneakyThrows
    private static void reply(HttpExchange exchange, int status) {
        exchange.sendResponseHeaders(status, NO_BODY);
        exchange.close();
    }
}
