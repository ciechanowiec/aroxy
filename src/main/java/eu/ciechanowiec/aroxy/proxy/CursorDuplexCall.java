package eu.ciechanowiec.aroxy.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jetty.client.AsyncRequestContent;
import org.eclipse.jetty.client.InputStreamResponseListener;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.io.Content;

/**
 * Owns the upload and response resources of one asynchronous Cursor HTTP/2 exchange.
 */
final class CursorDuplexCall {

    private static final int BUFFER = 8192;
    private final InputStream upload;
    private final AsyncRequestContent content;
    private final InputStreamResponseListener listener;

    CursorDuplexCall(InputStream upload) {
        this.upload = upload;
        this.content = new AsyncRequestContent();
        this.listener = new InputStreamResponseListener();
    }

    Request.Content content() {
        return this.content;
    }

    Response.Listener listener() {
        return this.listener;
    }

    void startUpload() {
        Thread.startVirtualThread(this::pump);
    }

    Response await(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        return this.listener.get(timeout, unit);
    }

    InputStream responseBody() {
        return this.listener.getInputStream();
    }

    void abort(Throwable failure) {
        this.content.fail(failure);
        try {
            this.listener.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    // Closing the content is the statement "that was the whole request body", so it belongs at the end
    // of a drained stream and nowhere else. Closing it from a try-with-resources would make the same
    // statement after a read that failed, handing the upstream a truncated request as a complete one and
    // leaving the failure to be reported after the vendor had already accepted it. The upload stream is
    // released either way, having no such meaning attached to its close.
    private void pump() {
        try (this.upload) {
            byte[] buffer = new byte[BUFFER];
            ByteBuffer chunk = ByteBuffer.wrap(buffer);
            int read = this.upload.read(buffer);
            while (read != -1) {
                chunk.clear().limit(read);
                Content.Sink.write(this.content, false, chunk);
                read = this.upload.read(buffer);
            }
            this.content.close();
        } catch (IOException exception) {
            this.content.fail(exception);
        }
    }
}
