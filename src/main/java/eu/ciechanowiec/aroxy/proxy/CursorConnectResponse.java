package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.CursorTransport;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.proxy.CursorHttp.WireFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import org.eclipse.jetty.io.Content;

/**
 * Restores Cursor Connect response envelopes while forwarding them to the CLI.
 */
@Justification(
    "composed class: it wires the two response dialects together, and coupling is capped by Checkstyle"
)
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
final class CursorConnectResponse {

    private final CursorTransport transport;
    private final Obfuscator obfuscator;
    private final ProtobufTransformer protobuf;

    CursorConnectResponse(
        CursorTransport transport, Obfuscator obfuscator, ProtobufTransformer protobuf
    ) {
        this.transport = transport;
        this.obfuscator = obfuscator;
        this.protobuf = protobuf;
    }

    void transform(
        Content.Sink response, InputStream body, WireFormat format, CursorTurn turn
    ) throws IOException {
        if (format == WireFormat.JSON) {
            this.json(response, body, turn);
        } else {
            this.protobuf(response, body, turn);
        }
    }

    private void json(
        Content.Sink response, InputStream body, CursorTurn turn
    ) throws IOException {
        CursorResponseStream stream = new CursorResponseStream(this.transport.json(), this.obfuscator);
        Reporter reporter = new Reporter(turn);
        try (body; OutputStream output = Content.Sink.asOutputStream(response)) {
            Optional<ConnectJsonFrame> frame = ConnectJsonFrame.read(body);
            while (frame.isPresent()) {
                turn.received(frame.orElseThrow().bytes());
                CursorResponseStream.Batch batch = stream.accept(frame.orElseThrow());
                CursorHttp.write(batch.frames(), output);
                reporter.when(batch.complete(), stream.reversedCount());
                frame = ConnectJsonFrame.read(body);
            }
            CursorHttp.write(stream.finish(), output);
        } finally {
            reporter.finish(stream.reversedCount());
        }
    }

    private void protobuf(
        Content.Sink response, InputStream body, CursorTurn turn
    ) throws IOException {
        CursorDeltaStream stream = new CursorDeltaStream(this.transport, this.obfuscator, this.protobuf);
        Reporter reporter = new Reporter(turn);
        try (body; OutputStream output = Content.Sink.asOutputStream(response)) {
            Optional<ConnectJsonFrame> frame = ConnectJsonFrame.read(body);
            while (frame.isPresent()) {
                ConnectJsonFrame current = frame.orElseThrow();
                turn.received(current.bytes());
                restore(stream, current, output);
                reporter.when(current.terminal(), stream.reversedCount());
                frame = ConnectJsonFrame.read(body);
            }
            flush(stream, output);
        } finally {
            reporter.finish(stream.reversedCount());
        }
    }

    // The remainder is flushed before the end-of-stream frame rather than after it, so a client that
    // stops reading at the terminal frame still receives the tail of the last delta.
    private static void restore(
        CursorDeltaStream stream, ConnectJsonFrame frame, OutputStream output
    ) throws IOException {
        CursorHttp.ensureUncompressed(frame);
        if (frame.terminal()) {
            flush(stream, output);
            frame.write(output);
        } else {
            stream.accept(frame).write(output);
        }
    }

    private static void flush(CursorDeltaStream stream, OutputStream output) throws IOException {
        CursorHttp.write(stream.finish().stream().toList(), output);
    }

    private static final class Reporter {

        private final CursorTurn completed;
        private boolean reported;

        Reporter(CursorTurn completed) {
            this.completed = completed;
            this.reported = false;
        }

        void when(boolean complete, int reversed) {
            if (complete && !this.reported) {
                this.completed.completed(reversed);
                this.reported = true;
            }
        }

        void finish(int reversed) {
            this.when(true, reversed);
        }
    }
}
