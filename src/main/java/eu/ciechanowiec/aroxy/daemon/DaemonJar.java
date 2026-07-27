package eu.ciechanowiec.aroxy.daemon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Gives a daemon its own immutable-once-started jar path.
 *
 * <p>The installed jar may be replaced while the JVM is alive. Executing from an atomic snapshot keeps
 * lazy class loading and shutdown hooks on the old inode, while the next launch receives the newly
 * installed artifact.
 *
 * <p>The same snapshot is what makes a running daemon outlive an upgrade, which is why
 * {@link #isStale()} exists: a daemon started before the install keeps serving the old code, and
 * without a word from the CLI that reads as though the new build were live.
 */
public final class DaemonJar {

    static final String FILE = ".daemon.jar";
    private final Path source;
    private final Path target;

    /**
     * Reads the staged jar of a home against the installed one.
     *
     * @param home the state directory holding the snapshot
     */
    public DaemonJar(Home home) {
        this(home, LaunchCommand.jar());
    }

    DaemonJar(Home home, String source) {
        this.source = Path.of(source);
        this.target = home.base().resolve(FILE);
    }

    /**
     * Whether the snapshot a running daemon executes differs from the installed jar, which means the
     * daemon predates the current build and a restart is needed to pick it up.
     *
     * @return whether the running daemon is on an older build
     */
    public boolean isStale() {
        return Files.isRegularFile(this.target)
            && Files.isRegularFile(this.source)
            && !sameContent(this.source, this.target);
    }

    String stage() {
        if (!Files.isRegularFile(this.source)) {
            return this.source.toString();
        }
        Path temporary = this.target.resolveSibling(FILE + ".new");
        try {
            Files.copy(this.source, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(
                temporary, this.target,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            );
            return this.target.toString();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not stage the aroxy daemon jar", exception);
        }
    }

    private static boolean sameContent(Path left, Path right) {
        try {
            return Files.size(left) == Files.size(right) && Files.mismatch(left, right) < 0;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not compare the aroxy daemon jar", exception);
        }
    }
}
