package eu.ciechanowiec.aroxy.config;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Validates that a file follows the stanza grammar before it is accepted as configuration. A
 * meaningful line (neither blank nor a comment) must be a {@code [gateway]}/{@code [rule]} header or
 * a {@code key = value} pair inside an already-opened stanza. A file of only blanks and comments is
 * valid (an emptied {@code default.conf} is a legitimate no-op). Any other file - a Markdown or
 * prose document - is rejected with an {@link InvalidConfigException} reporting how many of its
 * meaningful lines are not configuration, so the user sees that the whole file, not one stray line,
 * is the problem, instead of the file being silently ingested as an empty configuration.
 */
@UtilityClass
final class ConfigSyntax {

    private static final String COMMENT = "#";
    private static final char ASSIGN = '=';
    private static final String GATEWAY = "gateway";
    private static final String RULE = "rule";

    static void validate(String label, List<String> lines) {
        ConfigScan scan = new ConfigScan();
        IntStream.range(0, lines.size()).forEach(index -> classify(scan, index + 1, lines.get(index).strip()));
        scan.rejectIfInvalid(label);
    }

    private static void classify(ConfigScan scan, int lineNumber, String line) {
        boolean ignorable = line.isEmpty() || line.startsWith(COMMENT);
        boolean conforming = ignorable || header(scan, line) || pair(scan, line);
        scan.note(lineNumber, line, ignorable, conforming);
    }

    private static boolean header(ConfigScan scan, String line) {
        boolean bracketed = line.startsWith("[") && line.endsWith("]");
        boolean known = bracketed && isKnownName(line);
        scan.openStanza(known);
        return known;
    }

    private static boolean isKnownName(String line) {
        String name = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
        return GATEWAY.equals(name) || RULE.equals(name);
    }

    private static boolean pair(ConfigScan scan, String line) {
        int assign = line.indexOf(ASSIGN);
        return assign > 0 && scan.stanzaOpen() && isKey(line.substring(0, assign).strip());
    }

    private static boolean isKey(CharSequence candidate) {
        return !candidate.isEmpty() && candidate.chars().noneMatch(Character::isWhitespace);
    }
}
