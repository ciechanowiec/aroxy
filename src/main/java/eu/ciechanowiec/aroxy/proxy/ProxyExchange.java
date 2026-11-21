package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import eu.ciechanowiec.aroxy.client.BufferedTransport;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.obfuscation.ObfuscationResult;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;

/**
 * The single proxy exchange processor of one client's listener. It inflates a compressed request body
 * so there is text to work on, obfuscates it for the paths in that client's {@link ClientProfile}
 * scope, records the matches to the {@link AuditLog} and dumps the redacted request, forwards through
 * {@link Upstream}, and lets the {@link ProxyResponder} write the (de)obfuscated reply. A benign
 * client disconnect is swallowed quietly. A genuine upstream failure becomes an HTTP 502 with a small
 * JSON error body. An in-scope body that remains encoded is rejected locally, because forwarding bytes
 * the proxy cannot inspect would violate the data-minimization guarantee.
 */
@Slf4j
final class ProxyExchange implements HttpHandler {

    private static final int BAD_GATEWAY = 502;
    private static final int BAD_REQUEST = 400;
    private static final int UNSUPPORTED_MEDIA_TYPE = 415;
    private static final int UPGRADE_REQUIRED = 426;
    private final Obfuscator obfuscator;
    private final Upstream upstream;
    private final AuditLog audit;
    private final ClientProfile profile;
    private final BufferedTransport transport;
    private final ProxyResponder responder;

    ProxyExchange(ProxyParts parts, BufferedTransport transport) {
        this.obfuscator = parts.obfuscator();
        this.upstream = parts.upstream();
        this.audit = parts.audit();
        this.profile = parts.profile();
        this.transport = transport;
        this.responder = new ProxyResponder(
            parts.obfuscator(), parts.audit(), parts.profile(), transport
        );
    }

    @Override
    public void handle(HttpExchange exchange) {
        this.serve(exchange);
        exchange.close();
    }

    private void serve(HttpExchange exchange) {
        try {
            this.proxy(exchange);
        } catch (JSONException exception) {
            this.rejectInvalidJson(exchange, exception);
        } catch (IOException exception) {
            this.onIoFailure(exchange, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.sendError(exchange, exception);
        }
    }

    private void proxy(HttpExchange exchange) throws IOException, InterruptedException {
        ProxyRequest request = ProxyRequest.from(exchange).decoded();
        if (this.cannotTransform(request)) {
            rejectEncoding(exchange, request);
        } else if (WebSocketUpgrade.isRequested(request.headers())) {
            declineUpgrade(exchange);
        } else {
            this.forward(exchange, request);
        }
    }

    private boolean cannotTransform(ProxyRequest request) {
        return this.profile.scope().transforms(request.path()) && request.hasBody() && request.isEncoded();
    }

    private static void rejectEncoding(HttpExchange exchange, ProxyRequest request) throws IOException {
        log.atWarn()
            .addArgument(() -> request.encoding().orElse("unknown"))
            .addArgument(request::path)
            .log("Rejected an in-scope request with unsupported or malformed content encoding {} on {}");
        byte[] message = ("{\"type\":\"error\",\"error\":{\"type\":\"unsupported_encoding\","
            + "\"message\":\"aroxy could not safely decode this request body\"}}")
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", List.of("application/json"));
        exchange.sendResponseHeaders(UNSUPPORTED_MEDIA_TYPE, message.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(message);
        }
    }

    private void forward(HttpExchange exchange, ProxyRequest request) throws IOException, InterruptedException {
        String stamp = this.audit.nextStamp();
        byte[] outBody = this.obfuscate(request, stamp);
        UpstreamReply reply = this.upstream.send(
            request.method(), request.pathAndQuery(), request.headers(), outBody
        );
        this.responder.respond(exchange, reply, request, stamp);
    }

    /**
     * Refuses a WebSocket upgrade with the one status a client can act on immediately. Tunnelled
     * frames carry no body this proxy can obfuscate, so the exchange has to stay on HTTP.
     */
    private static void declineUpgrade(HttpExchange exchange) throws IOException {
        log.atDebug().addArgument(() -> exchange.getRequestURI().getRawPath()).log(
            "Refused a WebSocket upgrade on {}; this proxy transforms HTTP bodies only"
        );
        exchange.sendResponseHeaders(UPGRADE_REQUIRED, -1);
    }

    private byte[] obfuscate(ProxyRequest request, String stamp) {
        if (!this.transforms(request)) {
            return request.body();
        }
        ObfuscationResult result = new RequestObfuscation(this.transport.requestBody(), this.obfuscator)
            .apply(new String(request.body(), StandardCharsets.UTF_8));
        byte[] redacted = result.text().getBytes(StandardCharsets.UTF_8);
        this.audit.request(stamp, request.path(), result.matches());
        this.audit.wireRedactedRequest(stamp, redacted, WireBody.TEXT);
        return redacted;
    }

    private boolean transforms(ProxyRequest request) {
        return this.profile.scope().transforms(request.path())
            && request.hasBody()
            && !request.isEncoded();
    }

    private void onIoFailure(HttpExchange exchange, IOException failure) {
        if (ClientDisconnect.isBenign(failure)) {
            log.atDebug()
                .setMessage("Client disconnected before the response completed: {}")
                .addArgument(failure::getMessage)
                .log();
            return;
        }
        this.sendError(exchange, failure);
    }

    private void rejectInvalidJson(HttpExchange exchange, JSONException failure) {
        log.warn("Rejected an in-scope request that could not be parsed safely: {}", failure.getMessage());
        byte[] message = ("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_body\",\"message\":"
            + "\"aroxy could not safely parse this JSON request body\"}}")
            .getBytes(StandardCharsets.UTF_8);
        this.writeError(exchange, BAD_REQUEST, message);
    }

    private void sendError(HttpExchange exchange, Exception failure) {
        log.warn("Upstream proxying failed: {}", failure.toString());
        byte[] message = ("{\"type\":\"error\",\"error\":{\"type\":\"proxy_error\",\"message\":"
            + "\"aroxy could not reach the upstream: " + failure.getClass().getSimpleName()
            + "\"}}").getBytes(StandardCharsets.UTF_8);
        this.writeError(exchange, BAD_GATEWAY, message);
    }

    private void writeError(HttpExchange exchange, int status, byte[] message) {
        try {
            exchange.getResponseHeaders().put("Content-Type", List.of("application/json"));
            exchange.sendResponseHeaders(status, message.length);
            this.flush(exchange, message);
        } catch (IOException _) {
            // The client may already be gone, and there is nothing more to do.
        }
    }

    private void flush(HttpExchange exchange, byte[] message) throws IOException {
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(message);
        }
    }
}
