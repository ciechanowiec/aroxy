package eu.ciechanowiec.aroxy.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;

/**
 * Reads and writes the daemon's process id to {@link Home}{@code /aroxy.pid}, recording the process start time
 * alongside it. Liveness is checked via {@link ProcessHandle#of(long)} <em>and</em> a start-time match,
 * so a stale pid file left behind by a crashed daemon - whose numeric pid the OS may later recycle for
 * an unrelated process - reports as not running rather than letting a command act on the wrong process.
 */
public final class PidFile {

    private final Path file;

    /**
     * Creates a pid-file handle backed by the given file.
     *
     * @param file the {@code aroxy.pid} file to read and write
     */
    public PidFile(Path file) {
        this.file = file;
    }

    /**
     * Records the daemon pid alongside its process start time.
     *
     * @param pid the daemon process id to record
     */
    public void write(long pid) {
        this.writeContent(new PidRecord(pid, startInstantOf(pid)).serialized());
    }

    /**
     * The recorded pid, empty when the pid file is absent or unreadable.
     *
     * @return the recorded pid
     */
    public Optional<Long> read() {
        return this.stored().map(PidRecord::pid);
    }

    /**
     * Deletes the pid file, if present.
     */
    public void clear() {
        try {
            Files.deleteIfExists(this.file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not clear pid file " + this.file, exception);
        }
    }

    /**
     * Whether the recorded daemon is alive (pid present and its start time still matches).
     *
     * @return {@code true} if the recorded daemon process is running
     */
    public boolean isRunning() {
        return this.liveHandle().isPresent();
    }

    /**
     * The live process handle for the recorded daemon, present only when the pid is alive <em>and</em>
     * its start time still matches. Resolving the handle and verifying identity in one step lets a
     * caller act on exactly that process, with no window for the pid to be recycled between a separate
     * liveness check and the action.
     *
     * @return the live process handle for the recorded daemon, or empty when it is not running
     */
    public Optional<ProcessHandle> liveHandle() {
        return this.stored().flatMap(PidFile::matchingHandle);
    }

    private static Optional<ProcessHandle> matchingHandle(PidRecord pidRecord) {
        return ProcessHandle.of(pidRecord.pid()).filter(pidRecord::matches);
    }

    private static Optional<Instant> startInstantOf(long pid) {
        return ProcessHandle.of(pid).flatMap(handle -> handle.info().startInstant());
    }

    private void writeContent(CharSequence content) {
        Path temporary = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        try {
            Files.writeString(
                temporary, content,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            );
            Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write pid file " + this.file, exception);
        }
    }

    private Optional<PidRecord> stored() {
        if (!Files.exists(this.file)) {
            return Optional.empty();
        }
        return this.parseRecord();
    }

    private Optional<PidRecord> parseRecord() {
        try {
            return PidRecord.parse(Files.readString(this.file).strip());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read pid file " + this.file, exception);
        }
    }
}
