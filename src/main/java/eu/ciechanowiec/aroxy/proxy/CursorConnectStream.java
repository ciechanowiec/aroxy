package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.CursorTransport;
import eu.ciechanowiec.aroxy.proxy.CursorHttp.WireFormat;
import java.io.IOException;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/**
 * Transforms Cursor's bidirectional Connect envelopes while streaming them in both directions.
 */
final class CursorConnectStream implements AutoCloseable {

    private final CursorConnectContext context;
    private final CursorDuplexUpstream duplex;
    private final CursorConnectResponse response;

    CursorConnectStream(
        ProxyParts parts, ProtobufTransformer protobuf, CursorTransport transport
    ) {
        this.context = new CursorConnectContext(parts, protobuf);
        this.duplex = new CursorDuplexUpstream(parts.upstream().base());
        this.response = new CursorConnectResponse(transport, parts.obfuscator(), protobuf);
    }

    // The upload is completed in a finally rather than by a try-with-resources, because the order
    // matters and try-with-resources fixes it the wrong way round: it releases the pipe before any
    // finally runs, and the dump has to be written while the capture is still whole. A stream that is
    // cut off is exactly when the audit trail is worth having, so it is written on both paths.
    void serve(Request request, Response response) throws IOException {
        WireFormat format = CursorHttp.requireConnectContentType(request);
        CursorConnectUpload upload = new CursorConnectUpload(request, format, this.context);
        try {
            this.exchange(request, response, format, upload);
        } finally {
            upload.complete();
        }
    }

    private void exchange(
        Request request, Response response, WireFormat format, CursorConnectUpload upload
    ) throws IOException {
        UpstreamReply reply = this.upstream(request, upload);
        CursorHttp.prepare(response, reply.status(), reply.headers(), -1);
        this.response.transform(response, reply.body(), format, upload);
    }

    private UpstreamReply upstream(Request request, CursorConnectUpload upload) throws IOException {
        try {
            return this.duplex.send(
                request.getMethod(), CursorHttp.pathAndQuery(request),
                CursorHttp.headers(request), upload.body()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Cursor upstream connection was interrupted", exception);
        }
    }

    @Override
    public void close() {
        this.duplex.close();
    }
}
