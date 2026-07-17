package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import eu.ciechanowiec.aroxy.obfuscation.ObfuscationResult;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * The single proxy exchange processor. It obfuscates the request body for {@code /v1/messages}
 * paths, records the matches to the {@link AuditLog} and dumps the redacted request, forwards through
 * {@link Upstream}, and lets the {@link ProxyResponder} write the (de)obfuscated reply. A benign
 * client disconnect is swallowed quietly; a genuine upstream failure becomes an HTTP 502 with a small
 * JSON error body.
 */
@Slf4j
final class ProxyExchange implements HttpHandler {

    private static final int BAD_GATEWAY = 502;
    private final Obfuscator obfuscator;
    private final Upstream upstream;
    private final AuditLog audit;
    private final ProxyResponder responder;

    ProxyExchange(Obfuscator obfuscator, Upstream upstream, AuditLog audit) {
        this.obfuscator = obfuscator;
        this.upstream = upstream;
        this.audit = audit;
        this.responder = new ProxyResponder(obfuscator, audit);
    }

    @Override
    public void handle(HttpExchange exchange) {
        this.serve(exchange);
        exchange.close();
    }

    private void serve(HttpExchange exchange) {
        try {
            this.proxy(exchange);
        } catch (IOException exception) {
            this.onIoFailure(exchange, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.sendError(exchange, exception);
        }
    }

    private void proxy(HttpExchange exchange) throws IOException, InterruptedException {
        ProxyRequest request = ProxyRequest.from(exchange);
        String stamp = this.audit.nextStamp();
        byte[] outBody = this.obfuscate(request, stamp);
        UpstreamReply reply = this.upstream.send(
            request.method(), request.pathAndQuery(), request.headers(), outBody
        );
        this.responder.respond(exchange, reply, request, stamp);
    }

    private byte[] obfuscate(ProxyRequest request, String stamp) {
        if (!request.shouldObfuscate()) {
            return request.body();
        }
        ObfuscationResult result = this.obfuscator.obfuscate(new String(request.body(), StandardCharsets.UTF_8));
        byte[] redacted = result.text().getBytes(StandardCharsets.UTF_8);
        this.audit.request(stamp, request.path(), result.matches());
        this.audit.wireRedactedRequest(stamp, redacted);
        return redacted;
    }

    private void onIoFailure(HttpExchange exchange, IOException failure) {
        if (ClientDisconnect.isBenign(failure)) {
            log.debug("Client disconnected before the response completed: {}", failure.getMessage());
            return;
        }
        this.sendError(exchange, failure);
    }

    private void sendError(HttpExchange exchange, Exception failure) {
        log.warn("Upstream proxying failed: {}", failure.toString());
        byte[] message = ("{\"type\":\"error\",\"error\":{\"type\":\"proxy_error\",\"message\":"
            + "\"aroxy could not reach the upstream: " + failure.getClass().getSimpleName()
            + "\"}}").getBytes(StandardCharsets.UTF_8);
        this.writeError(exchange, message);
    }

    private void writeError(HttpExchange exchange, byte[] message) {
        try {
            exchange.getResponseHeaders().put("Content-Type", List.of("application/json"));
            exchange.sendResponseHeaders(BAD_GATEWAY, message.length);
            this.flush(exchange, message);
        } catch (IOException _) {
            // The client may already be gone; there is nothing more to do.
        }
    }

    private void flush(HttpExchange exchange, byte[] message) throws IOException {
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(message);
        }
    }
}
