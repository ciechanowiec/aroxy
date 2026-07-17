package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.fs.RestrictedDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes wire dumps under {@code logs/wire/}: the redacted (already obfuscated) request actually sent
 * upstream and the raw (token-bearing) upstream response. Neither artifact contains a secret, and the
 * pre-obfuscation original is never written by default. A retention sweep deletes dumps older than a
 * maximum age and keeps the directory under a maximum file count so multi-day runs stay bounded.
 *
 * <p>The sweep runs on the request thread after every dump, so it reads each entry's kind and
 * modification time exactly once, into a {@link Dump}. Ordering by modification time therefore costs
 * one stat per file rather than one per comparison, which is what handing a stat-reading key extractor
 * to {@link Comparator#comparing} over bare {@link Path}s would cost.
 */
@Slf4j
final class WireDumps {

    private static final Duration MAX_AGE = Duration.ofDays(7);
    private static final int MAX_FILES = 500;
    private final Path directory;

    WireDumps(Path directory) throws IOException {
        this.directory = directory;
        RestrictedDir.create(directory);
    }

    void redactedRequest(String stamp, byte[] body) {
        this.write(stamp + "-redacted-request.json", body);
    }

    void rawResponse(String stamp, byte[] body) {
        this.write(stamp + "-raw-response.txt", body);
    }

    private void write(String name, byte[] body) {
        try {
            Files.write(this.directory.resolve(name), body);
            this.sweep();
        } catch (IOException exception) {
            log.warn("Could not write wire dump {}: {}", name, exception.getMessage());
        }
    }

    private void sweep() throws IOException {
        try (Stream<Path> entries = Files.list(this.directory)) {
            List<Dump> dumps = entries.map(Dump::read)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Dump::modifiedAt))
                .toList();
            this.evict(dumps);
        }
    }

    private void evict(List<Dump> dumps) {
        Instant cutoff = Instant.now().minus(MAX_AGE);
        int count = dumps.size();
        int excess = Math.max(0, count - MAX_FILES);
        for (int index = 0; index < count; index++) {
            this.evictOne(dumps.get(index), index < excess, cutoff);
        }
    }

    private void evictOne(Dump dump, boolean overCount, Instant cutoff) {
        boolean tooOld = dump.modifiedAt().isBefore(cutoff);
        if (overCount || tooOld) {
            this.delete(dump.path());
        }
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Could not prune wire dump {}: {}", file, exception.getMessage());
        }
    }

    /**
     * One dump in the directory, paired with the modification time the sweep orders it by. Reading the
     * attributes once at listing time keeps the time a plain field, so sorting never touches the
     * filesystem. An entry whose attributes cannot be read is dropped rather than dated to the epoch:
     * it is either not a regular file or it vanished under the sweep, and in both cases there is
     * nothing to prune.
     *
     * @param path       the dump file
     * @param modifiedAt the modification time read when the directory was listed
     */
    private record Dump(Path path, Instant modifiedAt) {

        private static Optional<Dump> read(Path file) {
            try {
                return of(file, Files.readAttributes(file, BasicFileAttributes.class));
            } catch (IOException _) {
                return Optional.empty();
            }
        }

        private static Optional<Dump> of(Path file, BasicFileAttributes attributes) {
            if (!attributes.isRegularFile()) {
                return Optional.empty();
            }
            return Optional.of(new Dump(file, attributes.lastModifiedTime().toInstant()));
        }
    }
}
