package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An {@link OutputStream} that remembers whether a write to it failed. A streamed response moves
 * bytes from an upstream {@link java.io.InputStream} to a client sink, and both ends throw
 * {@link IOException}, so the exception alone never says which one broke. Wrapping the client sink
 * records the answer at the moment it happens, which is the only point where the two are still
 * distinguishable.
 */
final class ClientSink extends OutputStream {

    private final OutputStream delegate;
    private boolean broken;

    ClientSink(OutputStream delegate) {
        this.delegate = delegate;
        this.broken = false;
    }

    /**
     * Whether a write to the client end has failed.
     *
     * @return {@code true} when this sink threw, so the client is the end that broke
     */
    boolean isBroken() {
        return this.broken;
    }

    @Override
    public void write(int value) throws IOException {
        this.guarded(() -> this.delegate.write(value));
    }

    @Justification(
        "Qodana reads the inherited array parameter as non-null and wants the override to restate "
            + "that, while the project declares no nullability annotations to restate it with"
    )
    @SuppressWarnings("NullableProblems")
    @Override
    public void write(byte[] chunk, int offset, int length) throws IOException {
        this.guarded(() -> this.delegate.write(chunk, offset, length));
    }

    @Override
    public void flush() throws IOException {
        this.guarded(this.delegate::flush);
    }

    @Override
    public void close() throws IOException {
        this.delegate.close();
    }

    private void guarded(SinkWrite write) throws IOException {
        try {
            write.perform();
        } catch (IOException exception) {
            this.broken = true;
            throw exception;
        }
    }

    @FunctionalInterface
    private interface SinkWrite {

        void perform() throws IOException;
    }
}
