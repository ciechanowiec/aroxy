package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The publisher writes the counters at once on start, rewrites them only when they moved, and writes
 * the final counters on stop once its own thread is idle.
 */
class TrafficPublisherTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final TrafficTotals LATER = new TrafficTotals(1L, 1L, 1L, 1L, 1L, 1L);

    @Test
    void publishesAtOnceAndTheFinalTotalsOnStop(@TempDir Path base) {
        Path file = base.resolve("stats");
        AtomicReference<TrafficTotals> source = new AtomicReference<>(TrafficTotals.NONE);
        TrafficPublisher publisher = new TrafficPublisher(file, source::get);
        publisher.start();
        assertEquals(Optional.of(TrafficTotals.NONE), TrafficFile.read(file), "published on start");
        source.set(LATER);
        publisher.stop();
        assertEquals(Optional.of(LATER), TrafficFile.read(file), "the final totals are published on stop");
    }

    @Test
    @SneakyThrows
    void leavesTheFileAloneWhileNothingMoved(@TempDir Path base) {
        Path file = base.resolve("stats");
        TrafficPublisher publisher = new TrafficPublisher(file, () -> TrafficTotals.NONE);
        publisher.start();
        Files.delete(file);
        publisher.stop();
        assertFalse(Files.exists(file), "unchanged totals are not written again");
    }

    // A tick is held in progress, so the publisher thread is busy when stop is asked for. Interrupted
    // while waiting for it, stop must not write: the busy tick could still be writing the same file.
    @Test
    @SneakyThrows
    void skipsTheFinalWriteWhenStoppedWhileATickIsStillRunning(@TempDir Path base) {
        Path file = base.resolve("stats");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Supplier<TrafficTotals> source = () -> {
            if (calls.getAndIncrement() > 0) {
                hold(entered, release);
            }
            return TrafficTotals.NONE;
        };
        TrafficPublisher publisher = new TrafficPublisher(file, source);
        publisher.start();
        assertTrue(entered.await(WAIT.toMillis(), TimeUnit.MILLISECONDS), "a tick began");
        Thread.currentThread().interrupt();
        publisher.stop();
        boolean restored = Thread.interrupted();
        release.countDown();
        assertTrue(restored, "the interrupt is restored for the caller");
        assertEquals(Optional.of(TrafficTotals.NONE), TrafficFile.read(file), "stop wrote nothing");
    }

    @SneakyThrows
    private static void hold(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        release.await();
    }
}
