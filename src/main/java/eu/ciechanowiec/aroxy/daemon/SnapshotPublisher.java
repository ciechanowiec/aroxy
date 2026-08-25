package eu.ciechanowiec.aroxy.daemon;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the daemon's start time and the monotonic {@code generation} counter, and writes a fresh
 * {@link RuntimeSnapshot} to {@link Home}{@code /runtime} on every publish. The counter is bumped before each
 * write so the CLI, watching it, can tell that a reload it requested has actually been applied. All
 * publishes happen on the daemon's single synchronized path, so the counter needs no further guard.
 */
final class SnapshotPublisher {

    private final Path file;
    private final long startMillis;
    /**
     * The monotonic publish counter. Every publish still happens on the daemon's single synchronized
     * path, so this claims no more than it did before. The counter object is what keeps the field
     * final while the number it holds moves.
     */
    private final AtomicLong generation;

    SnapshotPublisher(Path file, long startMillis) {
        this.file = file;
        this.startMillis = startMillis;
        this.generation = new AtomicLong();
    }

    void publish(SnapshotData data) {
        long current = this.generation.incrementAndGet();
        new RuntimeSnapshot(
            data.gateways(), this.startMillis, current, data.files(), data.config()
        ).writeTo(this.file);
    }
}
