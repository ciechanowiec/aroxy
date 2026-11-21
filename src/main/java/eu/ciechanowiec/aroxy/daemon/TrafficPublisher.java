package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Publishes the live traffic counters to {@link Home}{@code /stats} once a second, for
 * {@code aroxy status} to read. The file is rewritten only when a counter moved, so an idle daemon
 * does not touch the disk, and a request never waits on file I/O because counting and publishing are
 * decoupled.
 *
 * <p>{@link #start()} publishes at once, so a status taken right after a start shows this session's
 * zeros rather than the previous session's totals.
 */
final class TrafficPublisher {

    private static final long INTERVAL_MILLIS = 1000L;
    private static final long STOP_WAIT_MILLIS = 1000L;
    private final Path file;
    private final Supplier<TrafficTotals> source;
    private final ScheduledExecutorService executor;
    /**
     * The totals last written. Written by {@link #start()} on the caller's thread, by each tick on the
     * publisher's, and by {@link #stop()} once the publisher is idle, so the reference is what makes
     * those writes safe.
     */
    private final AtomicReference<TrafficTotals> published;
    private final AtomicReference<Optional<ScheduledFuture<?>>> task;

    TrafficPublisher(Path file, Supplier<TrafficTotals> source) {
        this.file = file;
        this.source = source;
        this.executor = Executors.newSingleThreadScheduledExecutor(TrafficPublisher::thread);
        this.published = new AtomicReference<>(TrafficTotals.NONE);
        this.task = new AtomicReference<>(Optional.empty());
    }

    void start() {
        this.write(this.source.get());
        this.task.set(
            Optional.of(
                this.executor.scheduleAtFixedRate(this::tick, INTERVAL_MILLIS, INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            )
        );
    }

    /**
     * Stops publishing, writing the final totals once the publisher thread has finished, so the two
     * never write the file at the same time.
     */
    void stop() {
        Objects.requireNonNullElse(this.task.get(), Optional.<ScheduledFuture<?>>empty())
            .ifPresent(scheduled -> scheduled.cancel(false));
        this.executor.shutdown();
        if (this.awaitIdle()) {
            this.tick();
        }
    }

    private boolean awaitIdle() {
        try {
            return this.executor.awaitTermination(STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void tick() {
        TrafficTotals current = this.source.get();
        if (!current.equals(this.published.get())) {
            this.write(current);
        }
    }

    private void write(TrafficTotals totals) {
        StateFile.write(this.file, TrafficFile.lines(totals));
        this.published.set(totals);
    }

    private static Thread thread(Runnable work) {
        Thread worker = new Thread(work, "aroxy-stats-publisher");
        worker.setDaemon(true);
        return worker;
    }
}
