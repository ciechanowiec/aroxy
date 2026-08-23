package eu.ciechanowiec.aroxy.proxy;

import com.sun.net.httpserver.HttpExchange;
import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.BufferedTransport;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Reversal;
import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes an upstream reply back to the client. A response to a path in the serving
 * {@link ClientProfile} scope is deobfuscated: a {@code text/event-stream} body is streamed through
 * the {@link SseTransformer} in that client's dialect, any other body is buffered, reversed, and
 * written with a correct {@code Content-Length}. Every other response - and any out-of-scope path -
 * is copied through unchanged, which also passes an upstream {@code 429 + retry-after} straight to
 * the client.
 *
 * <p>The proxy asks the upstream for {@code identity} so the body stays transformable. Should the
 * upstream ignore that and answer with a real {@code content-encoding} anyway, the body is passed
 * through verbatim with its encoding header preserved instead of being fed (as compressed bytes) to
 * the deobfuscator, which would corrupt it.
 */
@Slf4j
final class ProxyResponder {

    private static final int BUFFER = 8192;
    private static final int SNIFF_LIMIT = 512;
    private static final int BYTE_ORDER_MARK = 0xFEFF;
    private static final List<String> SSE_PREFIXES = List.of("event:", "data:", "id:", "retry:", ":");
    private final Obfuscator obfuscator;
    private final AuditLog audit;
    private final ClientProfile profile;
    private final BufferedTransport transport;

    ProxyResponder(
        Obfuscator obfuscator, AuditLog audit, ClientProfile profile, BufferedTransport transport
    ) {
        this.obfuscator = obfuscator;
        this.audit = audit;
        this.profile = profile;
        this.transport = transport;
    }

    void respond(HttpExchange exchange, UpstreamReply reply, ProxyRequest request, String stamp) throws IOException {
        boolean encoded = hasEncoding(reply);
        PreparedReply prepared = prepare(reply, encoded);
        ResponseHeaderCopier.copy(prepared.reply().headers(), exchange.getResponseHeaders());
        preserveEncoding(prepared.reply(), exchange.getResponseHeaders());
        if (prepared.inferredStream()) {
            exchange.getResponseHeaders().put("Content-Type", List.of("text/event-stream"));
        }
        this.dispatch(
            exchange,
            prepared.reply(),
            this.plan(request, stamp, encoded, prepared.eventStream())
        );
    }

    private ResponsePlan plan(
        ProxyRequest request, String stamp, boolean encoded, boolean eventStream
    ) {
        boolean deobfuscate = this.profile.scope().transforms(request.path()) && !encoded;
        return new ResponsePlan(deobfuscate, !encoded && eventStream, stamp);
    }

    private static boolean isSse(UpstreamReply reply) {
        return HeaderLookup.first(reply.headers(), "content-type")
            .filter(type -> type.contains("text/event-stream")).isPresent();
    }

    private static boolean hasEncoding(UpstreamReply reply) {
        return encoding(reply).isPresent();
    }

    private static void preserveEncoding(UpstreamReply reply, Map<String, List<String>> downstream) {
        encoding(reply).ifPresent(value -> downstream.put("Content-Encoding", List.of(value)));
    }

    private static Optional<String> encoding(UpstreamReply reply) {
        return HeaderLookup.first(reply.headers(), "content-encoding")
            .filter(value -> !value.isBlank())
            .filter(value -> !"identity".equalsIgnoreCase(value));
    }

    /**
     * An upstream can carry a genuine SSE body under an ordinary JSON content type. Probe one already-
     * available prefix when the header does not identify a stream, then push those bytes back so the
     * normal pump still sees the complete body. This keeps header-correct streams on the zero-probe
     * path and never attempts to inspect a compressed response.
     */
    private static PreparedReply prepare(UpstreamReply reply, boolean encoded) throws IOException {
        boolean declared = !encoded && isSse(reply);
        if (encoded || declared) {
            return new PreparedReply(reply, declared, false);
        }
        return inferred(reply);
    }

    @Justification(
        "ownership of body moves into the returned reply and the response writer closes it"
    )
    @SuppressWarnings(
        "PMD.CloseResource"
    )
    private static PreparedReply inferred(UpstreamReply reply) throws IOException {
        PushbackInputStream body = new PushbackInputStream(reply.body(), SNIFF_LIMIT);
        byte[] prefix = new byte[SNIFF_LIMIT];
        int read = body.read(prefix);
        if (read > 0) {
            body.unread(prefix, 0, read);
        }
        boolean inferred = read > 0 && looksLikeSse(prefix, read);
        UpstreamReply replayable = new UpstreamReply(reply.status(), reply.headers(), body);
        return new PreparedReply(replayable, inferred, inferred);
    }

    private static boolean looksLikeSse(byte[] prefix, int length) {
        String text = new String(prefix, 0, length, StandardCharsets.UTF_8);
        int start = 0;
        while (
            start < text.length()
                && (Character.isWhitespace(text.charAt(start)) || text.charAt(start) == BYTE_ORDER_MARK)
        ) {
            start++;
        }
        String first = text.substring(start);
        return SSE_PREFIXES.stream().anyMatch(first::startsWith);
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
            pump(body, capture, new SseTransformer(out, deobfuscator, this.transport.dialect()));
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
        this.audit.response(stamp, this.profile.scope().auditLabel(), deobfuscator.reversedCount(), true);
        this.audit.wireRawResponse(stamp, capture.toByteArray(), WireBody.TEXT);
    }

    private void bufferedJson(HttpExchange exchange, UpstreamReply reply, String stamp) throws IOException {
        byte[] raw = readAll(reply);
        this.audit.wireRawResponse(stamp, raw, WireBody.TEXT);
        Reversal reversed = this.obfuscator.restore(new String(raw, StandardCharsets.UTF_8));
        this.audit.response(stamp, this.profile.scope().auditLabel(), reversed.count(), false);
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

    private record PreparedReply(UpstreamReply reply, boolean eventStream, boolean inferredStream) {
    }
}
