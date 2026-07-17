package eu.ciechanowiec.aroxy.governance;

import java.nio.file.Path;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Applies {@link TypographyRules} to every tracked text file, skipping content that is exempt by role:
 * the vendored Vale style library (a linter that by nature contains the very glyphs it detects), the
 * IDE configuration, and the vendored document theme assets. Binary files are skipped by content. The
 * excluded prefixes are logged by the caller so nothing is dropped silently.
 */
@UtilityClass
final class TypographyScanner {

    static final List<String> EXCLUDED_PREFIXES = List.of(".vale/", ".idea/", ".docs/themes/");

    static List<String> scan(Path root) {
        return Repository.trackedFiles(root).stream()
            .filter(file -> isIncluded(root, file))
            .flatMap(file -> violationsFor(root, file).stream())
            .toList();
    }

    private static boolean isIncluded(Path root, Path file) {
        String relative = root.relativize(file).toString();
        return EXCLUDED_PREFIXES.stream().noneMatch(relative::startsWith);
    }

    private static List<String> violationsFor(Path root, Path file) {
        String relative = root.relativize(file).toString();
        return Repository.readText(file)
            .map(content -> render(relative, content))
            .orElseGet(List::of);
    }

    private static List<String> render(String relative, String content) {
        return TypographyRules.findViolations(content).stream()
            .map(violation -> format(relative, violation))
            .toList();
    }

    private static String format(String relative, TypographyViolation violation) {
        return "%s:%d:%d banned code point U+%04X".formatted(
            relative, violation.lineNumber(), violation.column(), violation.codePoint()
        );
    }
}
