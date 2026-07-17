package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpExchange;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Reversal;
import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes an upstream reply back to the client. A {@code /v1/messages} response is deobfuscated: a
 * {@code text/event-stream} body is streamed through the {@link SseTransformer}, any other body is
 * buffered, reversed, and written with a correct {@code Content-Length}. Every other response - and a
 * non-{@code /v1/messages} path - is copied through unchanged, which also passes an upstream
 * {@code 429 + retry-after} straight to the client.
 *
 * <p>The proxy asks the upstream for {@code identity} so the body stays transformable. Should the
 * upstream ignore that and answer with a real {@code content-encoding} anyway, the body is passed
 * through verbatim with its encoding header preserved instead of being fed (as compressed bytes) to
 * the deobfuscator, which would corrupt it.
 */
@Slf4j
// composed class; coupling is governed by the project's own Checkstyle cap
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
final class ProxyResponder {

    private static final int BUFFER = 8192;
    private final Obfuscator obfuscator;
    private final AuditLog audit;

    ProxyResponder(Obfuscator obfuscator, AuditLog audit) {
        this.obfuscator = obfuscator;
        this.audit = audit;
    }

    void respond(HttpExchange exchange, UpstreamReply reply, ProxyRequest request, String stamp) throws IOException {
        ResponseHeaderCopier.copy(reply.headers(), exchange.getResponseHeaders());
        boolean encoded = preserveEncoding(reply, exchange.getResponseHeaders());
        this.dispatch(exchange, reply, plan(reply, request, stamp, encoded));
    }

    private static ResponsePlan plan(UpstreamReply reply, ProxyRequest request, String stamp, boolean encoded) {
        boolean deobfuscate = request.shouldDeobfuscate() && !encoded;
        boolean sse = !encoded && isSse(reply);
        return new ResponsePlan(deobfuscate, sse, stamp);
    }

    private static boolean isSse(UpstreamReply reply) {
        return HeaderLookup.first(reply.headers(), "content-type")
            .filter(type -> type.contains("text/event-stream")).isPresent();
    }

    private static boolean preserveEncoding(UpstreamReply reply, Map<String, List<String>> downstream) {
        Optional<String> encoding = HeaderLookup.first(reply.headers(), "content-encoding")
            .filter(value -> !value.isBlank())
            .filter(value -> !"identity".equalsIgnoreCase(value));
        encoding.ifPresent(value -> downstream.put("Content-Encoding", List.of(value)));
        return encoding.isPresent();
    }

    private void dispatch(HttpExchange exchange, UpstreamReply reply, ResponsePlan plan) throws IOException {
        if (plan.streamed()) {
            this.streamSse(exchange, reply, plan.stamp());
        } else if (plan.deobfuscate()) {
            this.bufferedJson(exchange, reply, plan.stamp());
        } else {
            passthrough(exchange, reply);
        }
    }

    private void streamSse(HttpExchange exchange, UpstreamReply reply, String stamp) throws IOException {
        exchange.sendResponseHeaders(reply.status(), 0);
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        StreamDeobfuscator deobfuscator = new StreamDeobfuscator(this.obfuscator);
        try (OutputStream out = exchange.getResponseBody(); InputStream body = reply.body()) {
            pump(body, capture, new SseTransformer(out, deobfuscator));
        } catch (IOException exception) {
            afterStreamInterrupted(exception);
        } finally {
            this.recordStream(stamp, capture, deobfuscator);
        }
    }

    /**
     * Handles a failure that surfaces after the {@code 200} stream headers were already sent. Nothing
     * can change the status now, so the failure is logged and swallowed here rather than propagating
     * to the error path, which would only attempt a second {@code sendResponseHeaders} that throws.
     */
    private static void afterStreamInterrupted(IOException failure) {
        if (ClientDisconnect.isBenign(failure)) {
            log.debug("SSE client disconnected mid-stream: {}", failure.getMessage());
        } else {
            log.warn("Upstream SSE stream failed after the response began: {}", failure.getMessage());
        }
    }

    private void recordStream(String stamp, ByteArrayOutputStream capture, StreamDeobfuscator deobfuscator) {
        this.audit.response(stamp, "/v1/messages", deobfuscator.reversedCount(), true);
        this.audit.wireRawResponse(stamp, capture.toByteArray());
    }

    private void bufferedJson(HttpExchange exchange, UpstreamReply reply, String stamp) throws IOException {
        byte[] raw = readAll(reply);
        this.audit.wireRawResponse(stamp, raw);
        Reversal reversed = this.obfuscator.restore(new String(raw, StandardCharsets.UTF_8));
        this.audit.response(stamp, "/v1/messages", reversed.count(), false);
        writeFixed(exchange, reply.status(), reversed.text().getBytes(StandardCharsets.UTF_8));
    }

    private static void passthrough(HttpExchange exchange, UpstreamReply reply) throws IOException {
        writeFixed(exchange, reply.status(), readAll(reply));
    }

    private static void pump(
        InputStream body, ByteArrayOutputStream capture, SseTransformer transformer
    ) throws IOException {
        byte[] chunk = new byte[BUFFER];
        int read = body.read(chunk);
        while (read != -1) {
            capture.write(chunk, 0, read);
            transformer.push(chunk, read);
            read = body.read(chunk);
        }
        transformer.finish();
    }

    private static byte[] readAll(UpstreamReply reply) throws IOException {
        try (InputStream body = reply.body()) {
            return body.readAllBytes();
        }
    }

    private static void writeFixed(HttpExchange exchange, int status, byte[] body) throws IOException {
        long length = body.length == 0 ? -1 : body.length;
        exchange.sendResponseHeaders(status, length);
        writeBody(exchange, body);
    }

    private static void writeBody(HttpExchange exchange, byte[] body) throws IOException {
        try (OutputStream out = exchange.getResponseBody()) {
            if (body.length > 0) {
                out.write(body);
            }
        }
    }
}
