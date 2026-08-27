package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.LockSupport;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Pins the bounded virtual-thread drain that every Jetty listener runs before it stops.
 */
class VirtualThreadExchangeTest {

    @Test
    @SneakyThrows
    void joinsAWorkerBeforeItsDeadline() {
        Thread worker = Thread.startVirtualThread(Thread::yield);
        VirtualThreadExchange.join(worker, Instant.now().plus(Duration.ofSeconds(1)));
        assertFalse(worker.isAlive());
    }

    @Test
    @SneakyThrows
    void restoresAnInterruptRaisedWhileJoining() {
        Thread worker = Thread.startVirtualThread(LockSupport::park);
        Thread.currentThread().interrupt();
        try {
            VirtualThreadExchange.join(worker, Instant.now().plus(Duration.ofSeconds(1)));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            boolean restored = Thread.interrupted();
            LockSupport.unpark(worker);
            worker.join();
            assertTrue(restored);
        }
    }
}
