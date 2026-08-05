package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/**
 * Handles Cursor methods whose request and response bodies do not require duplex streaming.
 */
@Justification(
    "composed class: it holds one whole buffered exchange, and coupling is capped by Checkstyle"
)
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
final class CursorBufferedRoutes {

    private static final String PROTO = "application/proto";
    private final ProxyParts parts;
    private final ProtobufTransformer protobuf;

    CursorBufferedRoutes(ProxyParts parts, ProtobufTransformer protobuf) {
        this.parts = parts;
        this.protobuf = protobuf;
    }

    void serve(Request request, Response response) throws IOException, InterruptedException {
        if (this.parts.profile().scope().transforms(request.getHttpURI().getPath())) {
            this.protobuf(request, response);
        } else {
            this.passthrough(request, response);
        }
    }

    // The audit record and both dumps are written before the reply is handed to the agent, which is
    // what the buffered clients do too. Writing them afterwards loses the proof of an exchange that
    // already happened whenever the agent has gone away by the time the reply is written.
    private void protobuf(Request request, Response response) throws IOException, InterruptedException {
        CursorHttp.requireContentType(
            request, PROTO, "Cursor input stream must use application/proto"
        );
        String endpoint = request.getHttpURI().getPath();
        String stamp = this.parts.audit().nextStamp();
        byte[] original = CursorHttp.bounded(Request.asInputStream(request));
        ProtobufTransformer.Outbound transformed = this.protobuf.outbound(original);
        this.parts.audit().request(stamp, endpoint, transformed.matches());
        this.parts.audit().wireRedactedRequest(stamp, transformed.payload(), WireBody.BINARY);
        UpstreamReply reply = this.parts.upstream().send(
            request.getMethod(), CursorHttp.pathAndQuery(request),
            CursorHttp.headers(request), transformed.payload()
        );
        byte[] raw = CursorHttp.bounded(reply.body());
        ProtobufTransformer.Inbound restored = this.protobuf.inbound(raw);
        this.parts.audit().wireRawResponse(stamp, raw, WireBody.BINARY);
        this.parts.audit().response(stamp, endpoint, restored.reversed(), false);
        CursorHttp.prepare(response, reply.status(), reply.headers(), restored.payload().length);
        CursorHttp.write(response, restored.payload());
    }

    private void passthrough(Request request, Response response) throws IOException, InterruptedException {
        byte[] body = read(Request.asInputStream(request));
        UpstreamReply reply = this.parts.upstream().send(
            request.getMethod(), CursorHttp.pathAndQuery(request), CursorHttp.headers(request), body
        );
        byte[] replyBody = read(reply.body());
        CursorHttp.prepare(response, reply.status(), reply.headers(), replyBody.length);
        HeaderLookup.first(reply.headers(), "content-encoding")
            .ifPresent(value -> response.getHeaders().put("Content-Encoding", value));
        CursorHttp.write(response, replyBody);
    }

    private static byte[] read(InputStream input) throws IOException {
        try (input) {
            return input.readAllBytes();
        }
    }
}
