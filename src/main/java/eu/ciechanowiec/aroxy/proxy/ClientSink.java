package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The client end of a streamed response. It remembers whether a write to it failed, and it keeps the
 * connection warm while the upstream is silent.
 *
 * <p>Both ends of a stream pump throw {@link IOException}, so the exception alone never says which
 * one broke. Wrapping the client sink records that at the moment it happens, which is the only point
 * where the two are still distinguishable.
 *
 * <p>The keep-alive exists because interposing a proxy makes this connection's liveness aroxy's
 * problem rather than the origin's. A vendor sends its own periodic events on a cadence tuned against
 * its clients, and a generation slow enough to fall between two of them leaves the client reading an
 * idle socket until it gives up. What is written is a comment line rather than an event: an SSE
 * comment is framing that every parser discards, so it invents none of the bookkeeping fields a
 * vendor's parser requires, which authoring a synthetic event would have to do. It is written only
 * between events, because {@link SseTransformer} emits one complete block per write.
 */
final class ClientSink extends OutputStream {

    private static final byte[] KEEP_ALIVE = ": keep-alive\n\n".getBytes(StandardCharsets.UTF_8);
    private final OutputStream delegate;
    private final Lock lock;
    private final AtomicLong lastWrite;
    private final AtomicBoolean broken;
    private final AtomicBoolean closed;
    private final AtomicInteger pulses;

    ClientSink(OutputStream delegate) {
        this.delegate = delegate;
        this.lock = new ReentrantLock();
        this.lastWrite = new AtomicLong(System.nanoTime());
        this.broken = new AtomicBoolean();
        this.closed = new AtomicBoolean();
        this.pulses = new AtomicInteger();
    }

    /**
     * Creates a sink that writes a keep-alive whenever the upstream has been silent for the given
     * interval. The interval is a parameter so a test can drive the behaviour without waiting out the
     * production cadence.
     *
     * @param delegate the client sink to wrap
     * @param interval how long the stream may be silent before a keep-alive is written
     * @return the wrapped sink, already pulsing
     */
    static ClientSink keepingAlive(OutputStream delegate, Duration interval) {
        ClientSink sink = new ClientSink(delegate);
        Thread.startVirtualThread(() -> sink.pulses(interval));
        return sink;
    }

    /**
     * Whether a write to the client end has failed.
     *
     * @return {@code true} when this sink threw, so the client is the end that broke
     */
    boolean isBroken() {
        return this.broken.get();
    }

    /**
     * How many keep-alives this sink has written, which counts the intervals the upstream spent
     * silent.
     *
     * @return the number of keep-alives written
     */
    int keepAlives() {
        return this.pulses.get();
    }

    /**
     * Whether this sink still has a client worth keeping warm. A closed stream has finished, and a
     * broken one has no reader left, so both end the pulse.
     *
     * @return whether keep-alives should continue
     */
    boolean pulsing() {
        return !this.closed.get() && !this.broken.get();
    }

    /**
     * Whether nothing has been written for a whole interval as of the given instant. Taking the
     * instant as a parameter keeps the decision a pure one a test can make without waiting.
     *
     * @param now      the instant to judge silence at, on {@link System#nanoTime()}'s scale
     * @param interval how long the stream may be silent before it counts as silent
     * @return whether the stream has been silent long enough to need a keep-alive
     */
    boolean silent(long now, Duration interval) {
        return now - this.lastWrite.get() >= interval.toNanos();
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
        this.closed.set(true);
        this.delegate.close();
    }

    private void guarded(SinkWrite write) throws IOException {
        this.lock.lock();
        try {
            this.perform(write);
        } finally {
            this.lock.unlock();
        }
    }

    private void perform(SinkWrite write) throws IOException {
        try {
            write.perform();
            this.lastWrite.set(System.nanoTime());
        } catch (IOException exception) {
            this.broken.set(true);
            throw exception;
        }
    }

    private void pulses(Duration interval) {
        while (this.awaitInterval(interval)) {
            this.pulseWhenSilent(interval);
        }
    }

    private boolean awaitInterval(Duration interval) {
        try {
            Thread.sleep(interval);
            return this.pulsing();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void pulseWhenSilent(Duration interval) {
        if (this.silent(System.nanoTime(), interval)) {
            this.pulse();
        }
    }

    private void pulse() {
        try {
            this.guarded(this::sendKeepAlive);
            this.pulses.incrementAndGet();
        } catch (IOException _) {
            // The client is already gone, which the pump reports with the bytes it captured.
        }
    }

    private void sendKeepAlive() throws IOException {
        this.delegate.write(KEEP_ALIVE);
        this.delegate.flush();
    }

    @FunctionalInterface
    private interface SinkWrite {

        void perform() throws IOException;
    }
}
