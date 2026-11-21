package eu.ciechanowiec.aroxy.daemon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads and publishes the line-oriented state files the daemon shares with the CLI, such as
 * {@link Home}{@code /runtime} and {@link Home}{@code /stats}. A publish replaces the file in one step,
 * so a CLI reading it concurrently sees either the old content or the new, never a half-written file:
 * the lines go to a sibling temporary file, which is then moved over the target atomically.
 *
 * <p>Failures are logged and otherwise absorbed. A state file exists for the CLI's benefit, so failing
 * to refresh it must not take the daemon down, and an unreadable one reads as absent.
 */
@Slf4j
@UtilityClass
final class StateFile {

    /**
     * Writes {@code lines} to {@code file}, replacing any previous content atomically.
     *
     * @param file  the state file to publish
     * @param lines the lines it should hold
     */
    static void write(Path file, List<String> lines) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(temporary, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            log.warn("Could not publish {}: {}", file, exception.getMessage());
        }
    }

    /**
     * Reads the lines of {@code file}.
     *
     * @param file the state file to read
     * @return its lines, or none when the file is absent or unreadable
     */
    static List<String> read(Path file) {
        return Files.exists(file) ? readExisting(file) : List.of();
    }

    /**
     * Parses a decimal field of a state file.
     *
     * @param value the field as written
     * @return the number, or {@code 0} when the field is not a number
     */
    static long number(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException _) {
            return 0L;
        }
    }

    private static List<String> readExisting(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException exception) {
            log.warn("Could not read {}: {}", file, exception.getMessage());
            return List.of();
        }
    }
}
