package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.fs.RestrictedDir;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The {@code ~/.aroxy} state directory layout. The base directory defaults to {@code ~/.aroxy} but
 * is overridable via the {@code AROXY_HOME} environment variable so tests can point it at a
 * {@code @TempDir}. Every accessor returns the canonical path for a state artifact; the directory
 * accessors create the directory on demand so callers never have to.
 *
 * @param base the base state directory, defaulting to {@code ~/.aroxy}
 */
public record Home(Path base) {

    private static final String ENV_VAR = "AROXY_HOME";
    private static final String PROPERTY = "aroxy.home";
    private static final String DEFAULT_DIR = ".aroxy";
    private static final String LOGS = "logs";

    /**
     * Resolves the home directory from the {@code AROXY_HOME} environment variable, then the
     * {@code aroxy.home} system property (used by tests), then the user's {@code ~/.aroxy}.
     *
     * @return the resolved home directory layout
     */
    public static Home resolve() {
        return new Home(locate());
    }

    private static Path locate() {
        return override().map(Path::of).orElseGet(Home::userDefault);
    }

    private static Optional<String> override() {
        Optional<String> environment = Optional.ofNullable(System.getenv(ENV_VAR)).filter(value -> !value.isBlank());
        return environment.or(
            () -> Optional.ofNullable(System.getProperty(PROPERTY)).filter(value -> !value.isBlank())
        );
    }

    private static Path userDefault() {
        return Path.of(System.getProperty("user.home")).resolve(DEFAULT_DIR);
    }

    /**
     * The base state directory, created (owner-only) on demand.
     *
     * @return the base directory
     */
    @Override
    public Path base() {
        return this.ensure(this.base);
    }

    /**
     * The daemon pid file.
     *
     * @return the {@code aroxy.pid} path
     */
    public Path pidFile() {
        return this.base().resolve("aroxy.pid");
    }

    /**
     * The daemon's published runtime snapshot file.
     *
     * @return the {@code runtime} path
     */
    public Path runtimeFile() {
        return this.base().resolve("runtime");
    }

    Path launchLock() {
        return this.base().resolve("launch.lock");
    }

    Path temporariesFile() {
        return this.base().resolve("temporaries");
    }

    Path reloadTrigger() {
        return this.base().resolve("reload");
    }

    /**
     * The materialized, user-editable default configuration file.
     *
     * @return the {@code default.conf} path
     */
    public Path defaultConfig() {
        return this.base().resolve("default.conf");
    }

    /**
     * The persistent attachment registry file.
     *
     * @return the {@code attachments} path
     */
    public Path attachments() {
        return this.base().resolve("attachments");
    }

    /**
     * The persisted, non-sensitive token vault.
     *
     * @return the {@code vault.tsv} path
     */
    public Path vault() {
        return this.base().resolve("vault.tsv");
    }

    Path logsDirectory() {
        return this.ensure(this.base().resolve(LOGS));
    }

    /**
     * The rolling application log.
     *
     * @return the {@code logs/aroxy.log} path
     */
    public Path appLog() {
        return this.logsDirectory().resolve("aroxy.log");
    }

    /**
     * The before/after audit log.
     *
     * @return the {@code logs/audit.log} path
     */
    public Path auditLog() {
        return this.logsDirectory().resolve("audit.log");
    }

    /**
     * The wire-dump directory, created (owner-only) on demand.
     *
     * @return the {@code logs/wire} path
     */
    public Path wireDirectory() {
        return this.ensure(this.logsDirectory().resolve("wire"));
    }

    Path stdoutLog() {
        return this.logsDirectory().resolve("daemon-out.log");
    }

    /**
     * The detached daemon's standard-error log, including failures that happen before it is ready.
     *
     * @return the {@code logs/daemon-err.log} path
     */
    public Path stderrLog() {
        return this.logsDirectory().resolve("daemon-err.log");
    }

    private Path ensure(Path directory) {
        try {
            return RestrictedDir.create(directory);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create directory " + directory, exception);
        }
    }
}
