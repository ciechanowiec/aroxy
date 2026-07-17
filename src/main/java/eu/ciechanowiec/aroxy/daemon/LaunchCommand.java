package eu.ciechanowiec.aroxy.daemon;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Resolves the pieces of the daemon launch command: the {@code java} binary that started this process
 * (from {@link ProcessHandle}) and the jar that contains this code (from its {@link CodeSource}). Both
 * fall back to sensible defaults so the command is always well-formed even outside a packaged jar.
 */
@UtilityClass
final class LaunchCommand {

    private static final String JAVA_HOME = "java.home";

    static String java() {
        return ProcessHandle.current().info().command().orElseGet(LaunchCommand::javaFromHome);
    }

    static String jar() {
        return location().map(Path::toString).orElseGet(LaunchCommand::jarFromProperty);
    }

    private static String javaFromHome() {
        return Path.of(System.getProperty(JAVA_HOME), "bin", "java").toString();
    }

    private static Optional<Path> location() {
        CodeSource source = LaunchCommand.class.getProtectionDomain().getCodeSource();
        return Optional.ofNullable(source).flatMap(LaunchCommand::toPath);
    }

    private static Optional<Path> toPath(CodeSource source) {
        try {
            return Optional.of(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException _) {
            return Optional.empty();
        }
    }

    private static String jarFromProperty() {
        return Path.of(System.getProperty("user.dir"), "target", "aroxy.jar").toString();
    }
}
