package eu.ciechanowiec.aroxy.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A persistent registry of attached configuration files, stored one absolute path per line. Attaching
 * is idempotent (a path already present is not appended again); detaching removes a path; listing
 * returns the current set in order. A missing backing file is treated as an empty registry.
 */
public final class AttachmentRegistry {

    private final Path file;

    /**
     * Creates a registry backed by the given file.
     *
     * @param file the file the attached paths are stored in, one absolute path per line
     */
    public AttachmentRegistry(Path file) {
        this.file = file;
    }

    /**
     * Attaches a path, idempotently: a path already present is not appended again.
     *
     * @param path the configuration file path to attach
     */
    public void attach(Path path) {
        Path absolute = path.toAbsolutePath();
        List<Path> current = this.list();
        if (!current.contains(absolute)) {
            this.append(absolute);
        }
    }

    /**
     * Detaches a path; a no-op when the path is not present.
     *
     * @param path the configuration file path to detach
     */
    public void detach(Path path) {
        Path absolute = path.toAbsolutePath();
        List<Path> current = this.list();
        List<Path> retained = current.stream().filter(entry -> !entry.equals(absolute)).toList();
        if (retained.size() != current.size()) {
            this.writeAll(retained);
        }
    }

    /**
     * The currently attached paths, in order; empty when the backing file is missing.
     *
     * @return the attached paths in registry order
     */
    public List<Path> list() {
        if (!Files.exists(this.file)) {
            return List.of();
        }
        return this.readLines().stream().map(String::strip).filter(line -> !line.isEmpty()).map(Path::of).toList();
    }

    private List<String> readLines() {
        try {
            return Files.readAllLines(this.file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read registry " + this.file, exception);
        }
    }

    private void append(Path absolute) {
        Collection<Path> updated = new ArrayList<>(this.list());
        updated.add(absolute);
        this.writeAll(updated);
    }

    private void writeAll(Collection<Path> entries) {
        List<String> lines = entries.stream().map(Path::toString).toList();
        try {
            Files.write(this.file, lines);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write registry " + this.file, exception);
        }
    }
}
