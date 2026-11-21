package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.CursorTransport;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/**
 * Routes Cursor's streaming, compatibility, and control-plane methods.
 */
@Slf4j
final class CursorRouter implements AutoCloseable {

    private static final int BAD_GATEWAY = 502;
    private final CursorTransport transport;
    private final CursorConnectStream connect;
    private final CursorBufferedRoutes buffered;

    CursorRouter(ProxyParts parts, CursorTransport transport) {
        ProtobufTransformer protobuf = new ProtobufTransformer(
            new ProtobufText(parts.obfuscator())
        );
        this.transport = transport;
        this.connect = new CursorConnectStream(parts, protobuf, transport);
        this.buffered = new CursorBufferedRoutes(parts, protobuf);
    }

    void serve(Request request, Response response) {
        try {
            this.route(request, response);
        } catch (ConnectProtocolException exception) {
            log.warn("Rejected Cursor stream: {}", CursorHttp.message(exception));
            CursorHttp.reject(response, exception.status(), CursorHttp.message(exception));
        } catch (IOException exception) {
            this.onIoFailure(response, exception);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            CursorHttp.reject(response, BAD_GATEWAY, "Cursor upstream request was interrupted");
        }
    }

    private void route(Request request, Response response) throws IOException, InterruptedException {
        String path = request.getHttpURI().getPath();
        if (this.transport.isStreaming(path)) {
            this.connect.serve(request, response);
        } else {
            this.buffered.serve(request, response);
        }
    }

    private void onIoFailure(Response response, IOException failure) {
        if (ClientDisconnect.isBenign(failure)) {
            log.atDebug()
                .setMessage("Cursor client disconnected: {}")
                .addArgument(failure::getMessage)
                .log();
        } else {
            log.warn("Cursor proxying failed: {}", failure.toString());
            CursorHttp.reject(response, BAD_GATEWAY, "aroxy could not reach the Cursor upstream");
        }
    }

    @Override
    public void close() {
        this.connect.close();
    }
}
