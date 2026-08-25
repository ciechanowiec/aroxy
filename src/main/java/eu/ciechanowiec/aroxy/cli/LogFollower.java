package eu.ciechanowiec.aroxy.cli;

import eu.ciechanowiec.airness.Justification;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Follows a growing log file by remembering a byte offset and returning whatever was appended since
 * the last poll. This is the Java-side {@code tail -f}: the {@code logs} command polls in a loop, and
 * tests can append to a real temporary file and assert each {@link #poll()} sees the new bytes. A file that
 * does not yet exist (or has shrunk on rotation) is handled by resetting the offset.
 */
final class LogFollower {

    private final Path file;
    /**
     * How far into the file the last poll read. One follower is polled by one command loop, so this
     * holds sequential state. It is atomic so the field can be final, not because it is shared.
     */
    private final AtomicLong offset;

    LogFollower(Path file) {
        this.file = file;
        this.offset = new AtomicLong();
    }

    Optional<String> poll() {
        if (!Files.exists(this.file)) {
            return Optional.empty();
        }
        return this.read();
    }

    @Justification(
        "the caught exception covers its subtypes uniformly"
    )
    @SuppressWarnings(
        "OverlyBroadCatchBlock"
    )
    private Optional<String> read() {
        try (RandomAccessFile handle = new RandomAccessFile(this.file.toFile(), "r")) {
            return this.readFrom(handle);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not follow " + this.file, exception);
        }
    }

    private Optional<String> readFrom(RandomAccessFile handle) throws IOException {
        long length = handle.length();
        this.resetIfTruncated(length);
        long from = this.offset.get();
        if (length == from) {
            return Optional.empty();
        }
        byte[] chunk = new byte[(int) (length - from)];
        handle.seek(from);
        handle.readFully(chunk);
        this.offset.set(length);
        return Optional.of(new String(chunk, StandardCharsets.UTF_8));
    }

    private void resetIfTruncated(long length) {
        if (length < this.offset.get()) {
            this.offset.set(0L);
        }
    }
}
