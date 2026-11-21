package eu.ciechanowiec.aroxy.daemon;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.fs.RestrictedDir;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The {@code ~/.aroxy} state directory layout. The base directory defaults to {@code ~/.aroxy} but
 * is overridable via the {@code AROXY_HOME} environment variable so tests can point it at a
 * {@code @TempDir}. Every accessor returns the canonical path for a state artifact. The directory
 * accessors create the directory on demand so callers never have to.
 *
 * <p>The default reads the {@code HOME} environment variable before the {@code user.home} system
 * property, because a JVM on macOS derives {@code user.home} from the account database and ignores
 * {@code HOME}. Reading the property first makes an installer script and the JVM it invokes disagree
 * about which state directory they mean, so a run under an overridden {@code HOME} cleans a sandbox
 * while stopping the real daemon.
 *
 * @param base the base state directory, defaulting to {@code ~/.aroxy}
 */
public record Home(Path base) {

    private static final String ENV_VAR = "AROXY_HOME";
    private static final String PROPERTY = "aroxy.home";
    private static final String HOME_VAR = "HOME";
    private static final String HOME_PROPERTY = "user.home";
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

    @SuppressFBWarnings(
        value = "ENV_USE_PROPERTY_INSTEAD_OF_ENV",
        justification = "the portable property this detector recommends is the very value that must not "
            + "win here, because a JVM on macOS reads it from the account database and so ignores an "
            + "overridden HOME"
    )
    @Justification(
        "an overridden HOME must win, and the user.home property on macOS comes from the account database instead"
    )
    @SuppressWarnings(
        "AirnessPropertyOverEnvironment"
    )
    private static Path locate() {
        Optional<String> variable = Optional.ofNullable(System.getenv(HOME_VAR));
        String property = System.getProperty(HOME_PROPERTY);
        return override(System.getenv(ENV_VAR), System.getProperty(PROPERTY))
            .map(Path::of)
            .orElseGet(() -> userDefault(variable, property));
    }

    /**
     * The environment wins over the property, and a value that is present but blank counts as absent so
     * that an empty variable does not resolve the home to nowhere. Both sources are taken as parameters
     * because a test cannot set an environment variable in its own process, and the rule about blankness
     * is the part worth testing rather than the reading.
     *
     * @param environment the value of the environment variable, or nothing when it is unset
     * @param property    the value of the system property, or nothing when it is unset
     * @return the override to use, or nothing when neither source names one
     */
    static Optional<String> override(@Nullable String environment, @Nullable String property) {
        return Optional.ofNullable(environment)
            .filter(value -> !value.isBlank())
            .or(() -> Optional.ofNullable(property).filter(value -> !value.isBlank()));
    }

    /**
     * The default state directory for a user whose home is described by the given pair. It is exposed
     * so a test can pin the precedence without setting a process environment variable.
     *
     * @param variable the value of the {@code HOME} environment variable, empty when it is unset
     * @param property the value of the {@code user.home} system property, used when the variable
     *                 names nothing usable
     * @return the base state directory the two values resolve to
     */
    static Path userDefault(Optional<String> variable, String property) {
        return Path.of(variable.filter(value -> !value.isBlank()).orElse(property)).resolve(DEFAULT_DIR);
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
