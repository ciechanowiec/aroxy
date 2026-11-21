package eu.ciechanowiec.aroxy.proxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.util.Callback;
import org.jspecify.annotations.Nullable;

/**
 * Captures Jetty content writes while accepting its null final-buffer signal.
 */
final class CapturingSink implements Content.Sink {

    private final ByteArrayOutputStream output;

    CapturingSink() {
        this.output = new ByteArrayOutputStream();
    }

    @Override
    public void write(boolean last, @Nullable ByteBuffer buffer, Callback callback) {
        Optional.ofNullable(buffer).ifPresent(this::append);
        callback.succeeded();
    }

    byte[] bytes() {
        return this.output.toByteArray();
    }

    private void append(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        this.output.writeBytes(bytes);
    }
}
