package eu.ciechanowiec.aroxy.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Optional;
import org.eclipse.jetty.server.Request;

/**
 * Connects Cursor's inbound request stream to the body consumed by the upstream HTTP/2 client.
 */
final class CursorUploadPipe implements AutoCloseable {

    private static final int BUFFER = 64 * 1024;
    private final InputStream client;
    private final PipedInputStream upstream;
    private final PipedOutputStream writer;

    CursorUploadPipe(Request request) throws IOException {
        this.client = Request.asInputStream(request);
        this.upstream = new PipedInputStream(BUFFER);
        this.writer = new PipedOutputStream(this.upstream);
    }

    Optional<ConnectJsonFrame> read() throws IOException {
        return ConnectJsonFrame.read(this.client);
    }

    void write(ConnectJsonFrame frame) throws IOException {
        frame.write(this.writer);
    }

    InputStream body() {
        return this.upstream;
    }

    void finish() throws IOException {
        this.writer.close();
    }

    @Override
    public void close() throws IOException {
        try (this.client; this.writer; this.upstream) {
            this.writer.flush();
        }
    }
}
