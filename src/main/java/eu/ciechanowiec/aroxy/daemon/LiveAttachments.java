package eu.ciechanowiec.aroxy.daemon;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The daemon's in-memory record of when each contributing file was first loaded. The temporary
 * attachment <em>list</em> lives in the {@link ControlFiles} temporaries file. This class keeps only
 * the per-file load timestamps, so {@code status} and {@code config list} can show a load time for
 * every contributing file.
 */
final class LiveAttachments {

    private final Map<Path, Long> loadedAtMillis;

    LiveAttachments() {
        this.loadedAtMillis = new ConcurrentHashMap<>();
    }

    /**
     * Records the first-seen time for any path not stamped yet. Already-known paths keep theirs.
     */
    void stamp(Iterable<Path> paths, long millis) {
        paths.forEach(path -> this.loadedAtMillis.putIfAbsent(path, millis));
    }

    long loadedAt(Path path) {
        return this.loadedAtMillis.getOrDefault(path, 0L);
    }
}
