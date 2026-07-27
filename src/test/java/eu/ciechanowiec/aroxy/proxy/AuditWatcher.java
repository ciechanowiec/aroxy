package eu.ciechanowiec.aroxy.proxy;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Waits for audit-file content through filesystem notifications, without timing sleeps.
 */
final class AuditWatcher implements AutoCloseable {

    private final Path audit;
    private final WatchService watcher;

    AuditWatcher(Path logs) throws IOException {
        this.audit = logs.resolve("audit.log");
        this.watcher = logs.getFileSystem().newWatchService();
        logs.register(this.watcher, ENTRY_CREATE, ENTRY_MODIFY);
    }

    boolean await(CharSequence expected, Duration timeout) throws IOException, InterruptedException {
        boolean found = this.contains(expected);
        if (!found) {
            Optional<WatchKey> event = Optional.ofNullable(
                this.watcher.poll(timeout.toNanos(), TimeUnit.NANOSECONDS)
            );
            event.ifPresent(AuditWatcher::clear);
            found = event.isPresent() && this.contains(expected);
        }
        return found;
    }

    String contents() {
        try {
            return Files.exists(this.audit) ? Files.readString(this.audit) : "(audit file absent)";
        } catch (IOException _) {
            return "(audit file unreadable)";
        }
    }

    private boolean contains(CharSequence expected) throws IOException {
        return Files.exists(this.audit) && Files.readString(this.audit).contains(expected);
    }

    private static void clear(WatchKey key) {
        key.pollEvents();
        key.reset();
    }

    @Override
    public void close() throws IOException {
        this.watcher.close();
    }
}
