package eu.ciechanowiec.aroxy.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

/**
 * The filesystem control channel that replaces the former local admin HTTP server. Two files under
 * the state directory carry every control signal that used to be an admin request: the
 * <em>reload trigger</em>, which holds a strictly increasing stamp the daemon polls to learn it should
 * rebuild its configuration, and the <em>temporaries file</em>, the session-scoped list of temporary
 * attachment paths. The stamp lives in the file content (not its modification time) and each touch
 * writes a value larger than the last, so two reloads within one filesystem mtime tick are still
 * distinguished. Because the channel is plain files inside the user's state directory, no process that
 * cannot already read those files (and no remote host or browser) can drive the daemon.
 */
public final class ControlFiles {

    private final Path trigger;
    private final Path temporaries;

    /**
     * Creates a control channel over the trigger and temporaries files inside the state directory.
     *
     * @param home the state directory layout the control files live under
     */
    public ControlFiles(Home home) {
        this.trigger = home.reloadTrigger();
        this.temporaries = home.temporariesFile();
    }

    private static Optional<Long> parseStamp(String text) {
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    /**
     * Writes a strictly larger stamp into the reload trigger so the daemon's poll notices and reloads.
     */
    public void touch() {
        String stamp = Long.toString(this.nextStamp());
        Path temporary = this.trigger.resolveSibling(this.trigger.getFileName() + ".tmp");
        try {
            Files.writeString(
                temporary, stamp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            );
            Files.move(temporary, this.trigger, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not touch reload trigger " + this.trigger, exception);
        }
    }

    /**
     * The reload trigger's current stamp, empty when the trigger is absent or unreadable.
     */
    Optional<Long> triggerStamp() {
        return Files.exists(this.trigger) ? this.readTriggerStamp() : Optional.empty();
    }

    private long nextStamp() {
        long previous = this.triggerStamp().orElse(0L);
        return Math.max(System.currentTimeMillis(), previous + 1);
    }

    /**
     * The temporary attachment paths recorded for this session, empty when none are recorded.
     *
     * @return the recorded temporary attachment paths
     */
    public List<Path> readTemporaries() {
        return Files.exists(this.temporaries) ? this.parseTemporaries() : List.of();
    }

    /**
     * Records one temporary attachment path, skipping it when already present.
     *
     * @param path the temporary attachment path to record
     */
    public void appendTemporary(Path path) {
        Path absolute = path.toAbsolutePath();
        if (!this.readTemporaries().contains(absolute)) {
            this.writeTemporary(absolute);
        }
    }

    /**
     * Clears the temporary list so temporaries never survive a daemon restart.
     */
    void clearTemporaries() {
        try {
            Files.deleteIfExists(this.temporaries);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not clear temporaries " + this.temporaries, exception);
        }
    }

    private Optional<Long> readTriggerStamp() {
        try {
            return parseStamp(Files.readString(this.trigger).strip());
        } catch (IOException _) {
            return Optional.empty();
        }
    }

    private List<Path> parseTemporaries() {
        try {
            return Files.readAllLines(this.temporaries).stream()
                .map(String::strip).filter(line -> !line.isEmpty()).map(Path::of).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read temporaries " + this.temporaries, exception);
        }
    }

    private void writeTemporary(Path absolute) {
        String line = absolute + System.lineSeparator();
        try {
            Files.writeString(
                this.temporaries, line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not append temporary " + this.temporaries, exception);
        }
    }
}
