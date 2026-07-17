package eu.ciechanowiec.aroxy.daemon;

import java.nio.file.Path;

/**
 * Owns the daemon's start time and the monotonic {@code generation} counter, and writes a fresh
 * {@link RuntimeSnapshot} to {@link Home}{@code /runtime} on every publish. The counter is bumped before each
 * write so the CLI, watching it, can tell that a reload it requested has actually been applied. All
 * publishes happen on the daemon's single synchronized path, so the counter needs no further guard.
 */
final class SnapshotPublisher {

    private final Path file;
    private final long startMillis;
    private long generation;

    SnapshotPublisher(Path file, long startMillis) {
        this.file = file;
        this.startMillis = startMillis;
        this.generation = 0;
    }

    void publish(SnapshotData data) {
        this.generation++;
        new RuntimeSnapshot(
            data.listenAddress(), data.upstream(), data.ruleCount(),
            this.startMillis, this.generation, data.files()
        ).writeTo(this.file);
    }
}
