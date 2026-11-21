package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import eu.ciechanowiec.aroxy.proxy.CursorHttp.WireFormat;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;

/**
 * Streams transformed Cursor request envelopes into the duplex upstream connection.
 */
@Slf4j
final class CursorConnectUpload implements CursorTurn {

    private final CursorUploadPipe pipe;
    private final WireFormat format;
    private final CursorConnectContext context;
    private final Thread pump;
    private final String stamp;
    private final BoundedCapture capture;

    CursorConnectUpload(
        Request request, WireFormat format, CursorConnectContext context
    ) throws IOException {
        this.pipe = new CursorUploadPipe(request);
        this.format = format;
        this.context = context;
        this.capture = new BoundedCapture();
        CursorOutboundFrame first = this.safelyFirst();
        this.stamp = this.context.nextStamp();
        this.context.request(this.stamp, first.matches());
        this.pump = Thread.startVirtualThread(() -> this.upload(first.frame()));
    }

    private CursorOutboundFrame safelyFirst() throws IOException {
        try {
            return this.first();
        } catch (IOException | ConnectProtocolException exception) {
            this.closeAfterFailure(exception);
            throw exception;
        }
    }

    InputStream body() {
        return this.pipe.body();
    }

    @Override
    public void completed(int reversed) {
        this.context.response(this.stamp, reversed);
    }

    @Override
    public void received(byte[] frame) {
        this.capture.received(frame);
    }

    /**
     * Ends this upload whichever way the exchange went: waits for the pump, writes the wire dumps,
     * and releases the pipe, in that order.
     *
     * <p>The order is the whole point. The dump has to be written while the capture is whole, and the
     * capture is whole only once the pump has stopped adding to it, so releasing the pipe first would
     * cut both short. Releasing it last also means an interrupted stream still leaves a dump, which is
     * the case a dump is worth the most.
     */
    void complete() {
        this.await();
        this.dump();
        this.release();
    }

    private void dump() {
        this.context.wire(this.stamp, this.capture.sentBytes(), this.capture.receivedBytes());
    }

    private void await() {
        try {
            this.pump.join();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    // The pipe is released here rather than thrown from, because this runs on the way out of an
    // exchange that may already be failing, and a close that failed too would replace the failure the
    // caller is about to report with a less informative one. It is not sorted into benign and not, the
    // way the router sorts a failure it is about to answer with a status: the exchange is over by now,
    // both readings lead to this same line, and the branch that told them apart would be one no test
    // can reach.
    private void release() {
        try {
            this.pipe.close();
        } catch (IOException exception) {
            log.atDebug()
                .setMessage("Cursor request pipe could not be released: {}")
                .addArgument(exception::getMessage)
                .log();
        }
    }

    private CursorOutboundFrame first() throws IOException {
        ConnectJsonFrame frame = this.pipe.read()
            .orElseThrow(() -> new ConnectProtocolException("Cursor Connect request has no envelope"));
        return this.context.transform(frame, this.format);
    }

    private void closeAfterFailure(Exception failure) {
        try {
            this.pipe.close();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    // The pipe is finished in a finally because the upstream learns the request is over by reading
    // end-of-input. Leaving it open on a failure would leave the upstream reader blocked until it
    // noticed this thread had died, which PipedInputStream reports only after a polling interval:
    // a clean end of body is both faster and the truth.
    private void upload(ConnectJsonFrame first) {
        try {
            this.uploadFrames(first);
        } catch (IOException | ConnectProtocolException exception) {
            log.atDebug()
                .setMessage("Cursor request upload ended: {}")
                .addArgument(exception::getMessage)
                .log();
        } finally {
            this.finish();
        }
    }

    private void finish() {
        try {
            this.pipe.finish();
        } catch (IOException exception) {
            log.atDebug()
                .setMessage("Cursor request upload could not be closed: {}")
                .addArgument(exception::getMessage)
                .log();
        }
    }

    private void uploadFrames(ConnectJsonFrame first) throws IOException {
        ConnectJsonFrame frame = first;
        this.capture.sent(frame.bytes());
        this.pipe.write(frame);
        while (!frame.terminal()) {
            Optional<ConnectJsonFrame> next = this.pipe.read();
            if (next.isEmpty()) {
                break;
            }
            CursorOutboundFrame transformed = this.context.transform(
                next.orElseThrow(), this.format
            );
            this.audit(transformed.matches());
            frame = transformed.frame();
            this.capture.sent(frame.bytes());
            this.pipe.write(frame);
        }
    }

    private void audit(Collection<ObfuscationMatch> matches) {
        if (!matches.isEmpty()) {
            this.context.request(this.stamp, matches);
        }
    }
}
