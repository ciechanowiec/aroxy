package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.BufferedTransport;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.CopilotTransport;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Reversal;
import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Response;

/**
 * Writes a Copilot upstream reply after applying the response shape selected by its inference path.
 */
@Slf4j
@Justification(
    "a composed response boundary whose coupling is governed by the project's Checkstyle cap"
)
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
final class CopilotResponder {

    private static final int BUFFER = 8192;
    private final Obfuscator obfuscator;
    private final AuditLog audit;
    private final ClientProfile profile;
    private final CopilotTransport transport;

    CopilotResponder(ProxyParts parts, CopilotTransport transport) {
        this.obfuscator = parts.obfuscator();
        this.audit = parts.audit();
        this.profile = parts.profile();
        this.transport = transport;
    }

    void respond(Response response, UpstreamReply reply, ProxyRequest request, String stamp) throws IOException {
        boolean encoded = encoding(reply).isPresent();
        boolean transformed = this.transforms(request.path(), encoded);
        boolean stream = transformed && isSse(reply);
        ResponseHeaderCopier.copy(reply.headers(), response.getHeaders());
        encoding(reply).ifPresent(value -> response.getHeaders().put("Content-Encoding", value));
        this.write(response, new ResponseContext(reply, request.path(), stamp, transformed, stream));
    }

    private boolean transforms(String path, boolean encoded) {
        return this.profile.scope().transforms(path) && !encoded;
    }

    private void write(Response response, ResponseContext context) throws IOException {
        if (context.stream()) {
            this.stream(response, context.reply(), context.path(), context.stamp());
        } else if (context.transformed()) {
            this.buffered(response, context.reply(), context.stamp());
        } else {
            passthrough(response, context.reply());
        }
    }

    private void stream(
        Response response, UpstreamReply reply, String path, String stamp
    ) throws IOException {
        response.setStatus(reply.status());
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        ClientSink sink = ClientSink.keepingAlive(Content.Sink.asOutputStream(response));
        try (sink; InputStream body = reply.body()) {
            this.transform(new StreamContext(body, sink, capture, stamp), path);
        } catch (IOException exception) {
            this.broken(stamp, capture, sink, exception);
            throw exception;
        }
    }

    private void transform(StreamContext context, String path) throws IOException {
        if (this.transport.chatCompletions(path)) {
            this.chat(context);
        } else {
            this.generic(context, path);
        }
    }

    /**
     * Records what a stream that never completed managed to carry. The successful path dumps its
     * capture only after the pump loop ends, so without this the one case worth investigating is the
     * one case that leaves no wire evidence.
     *
     * @param stamp     the audit stamp the partial dump is filed under
     * @param capture   the bytes the stream carried before it ended
     * @param sink      the client end, which knows whether it broke and how long it was kept warm
     * @param exception the failure that ended the stream
     */
    private void broken(String stamp, ByteArrayOutputStream capture, ClientSink sink, IOException exception) {
        this.audit.wireRawResponse(stamp, capture.toByteArray(), WireBody.TEXT);
        log.warn(
            "Copilot stream broke on the {} end after {} captured byte(s) and {} keep-alive(s), "
                + "dumped as partial: {}",
            sink.isBroken() ? "client" : "upstream", capture.size(), sink.keepAlives(), exception.toString()
        );
    }

    private void chat(StreamContext context) throws IOException {
        ChatCompletionTransformer transformer = new ChatCompletionTransformer(
            context.output(), this.obfuscator
        );
        pump(context.body(), context.capture(), transformer);
        this.auditResponse(context.stamp(), context.capture(), transformer.reversedCount());
    }

    private void generic(StreamContext context, String path) throws IOException {
        BufferedTransport buffered = this.transport.buffered(path).orElseThrow(
            () -> new IllegalStateException("No Copilot SSE dialect is registered for " + path)
        );
        StreamDeobfuscator deobfuscator = new StreamDeobfuscator(this.obfuscator);
        SseTransformer transformer = new SseTransformer(
            context.output(), deobfuscator, buffered.dialect()
        );
        pump(context.body(), context.capture(), transformer);
        this.auditResponse(context.stamp(), context.capture(), deobfuscator.reversedCount());
    }

    private void buffered(Response response, UpstreamReply reply, String stamp) throws IOException {
        byte[] raw = read(reply.body());
        this.audit.wireRawResponse(stamp, raw, WireBody.TEXT);
        Reversal reversed = this.obfuscator.restore(new String(raw, StandardCharsets.UTF_8));
        this.audit.response(stamp, this.profile.scope().auditLabel(), reversed.count(), false);
        byte[] body = reversed.text().getBytes(StandardCharsets.UTF_8);
        CursorHttp.prepare(response, reply.status(), reply.headers(), body.length);
        CursorHttp.write(response, body);
    }

    private void auditResponse(String stamp, ByteArrayOutputStream capture, int reversed) {
        this.audit.response(stamp, this.profile.scope().auditLabel(), reversed, true);
        this.audit.wireRawResponse(stamp, capture.toByteArray(), WireBody.TEXT);
    }

    private static void passthrough(Response response, UpstreamReply reply) throws IOException {
        byte[] body = read(reply.body());
        CursorHttp.prepare(response, reply.status(), reply.headers(), body.length);
        encoding(reply).ifPresent(value -> response.getHeaders().put("Content-Encoding", value));
        CursorHttp.write(response, body);
    }

    private static Optional<String> encoding(UpstreamReply reply) {
        return HeaderLookup.first(reply.headers(), "content-encoding")
            .filter(value -> !value.isBlank())
            .filter(value -> !"identity".equalsIgnoreCase(value));
    }

    private static boolean isSse(UpstreamReply reply) {
        return HeaderLookup.first(reply.headers(), "content-type")
            .filter(type -> type.contains("text/event-stream")).isPresent();
    }

    private static byte[] read(InputStream input) throws IOException {
        try (input) {
            return input.readAllBytes();
        }
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

    private static void pump(
        InputStream body, ByteArrayOutputStream capture, ChatCompletionTransformer transformer
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

    private record StreamContext(
        InputStream body, OutputStream output, ByteArrayOutputStream capture, String stamp
    ) {
    }

    private record ResponseContext(
        UpstreamReply reply, String path, String stamp, boolean transformed, boolean stream
    ) {
    }
}
