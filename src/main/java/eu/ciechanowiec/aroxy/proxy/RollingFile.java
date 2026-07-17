package eu.ciechanowiec.aroxy.proxy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * Appends text to a file with simple size-based rotation: when the active file exceeds a byte limit
 * it is rolled to {@code name.1}, {@code name.1} to {@code name.2}, and so on, keeping at most a
 * fixed number of backups so a long-running daemon never grows the log without bound. All appends
 * are serialized through this object's monitor.
 */
@Slf4j
final class RollingFile {

    private static final long MAX_BYTES = 4L * 1024L * 1024L;
    private static final int MAX_BACKUPS = 3;
    private final Path file;
    private final ReentrantLock lock;

    RollingFile(Path file) {
        this.file = file;
        this.lock = new ReentrantLock();
    }

    void append(CharSequence text) {
        this.lock.lock();
        try {
            this.rollIfNeeded();
            Files.writeString(
                this.file, text,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            log.warn("Could not write {}: {}", this.file, exception.getMessage());
        } finally {
            this.lock.unlock();
        }
    }

    private void rollIfNeeded() throws IOException {
        boolean tooBig = Files.exists(this.file) && Files.size(this.file) > MAX_BYTES;
        if (tooBig) {
            this.shiftBackups();
        }
    }

    private void shiftBackups() throws IOException {
        for (int index = MAX_BACKUPS - 1; index >= 1; index--) {
            this.shiftOne(index);
        }
        Files.move(this.file, this.backup(1), StandardCopyOption.REPLACE_EXISTING);
    }

    private void shiftOne(int index) throws IOException {
        Path source = this.backup(index);
        if (Files.exists(source)) {
            Files.move(source, this.backup(index + 1), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path backup(int index) {
        return this.file.resolveSibling(this.file.getFileName() + "." + index);
    }
}
