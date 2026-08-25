package eu.ciechanowiec.aroxy.config;

import java.util.Collection;
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

    static void validate(String label, Collection<String> lines) {
        List<String> stripped = lines.stream().map(String::strip).toList();
        int opened = firstStanza(stripped);
        List<Integer> offending = IntStream.range(0, stripped.size())
            .filter(index -> offends(stripped.get(index), index > opened))
            .boxed()
            .toList();
        if (!offending.isEmpty()) {
            long meaningful = stripped.stream().filter(ConfigSyntax::meaningful).count();
            int first = offending.getFirst();
            throw new InvalidConfigException(
                label, summary(offending.size(), meaningful, first + 1, stripped.get(first))
            );
        }
    }

    /**
     * The index of the first line that opens a stanza, or a value no line index can reach when the
     * file opens none. Every later line sits inside a stanza, and that is the only thing a pair needs
     * to know, so the running tally the scan used to carry reduces to this one number.
     *
     * @param lines the stripped lines of the file
     * @return the index of the first stanza header
     */
    private static int firstStanza(List<String> lines) {
        return IntStream.range(0, lines.size())
            .filter(index -> header(lines.get(index)))
            .findFirst()
            .orElse(Integer.MAX_VALUE);
    }

    private static boolean meaningful(String line) {
        return !line.isEmpty() && !line.startsWith(COMMENT);
    }

    private static boolean offends(String line, boolean stanzaOpen) {
        return meaningful(line) && !conforming(line, stanzaOpen);
    }

    private static boolean conforming(String line, boolean stanzaOpen) {
        return header(line) || pair(line, stanzaOpen);
    }

    private static boolean header(String line) {
        return line.startsWith("[") && line.endsWith("]") && isKnownName(line);
    }

    private static boolean isKnownName(String line) {
        String name = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
        return GATEWAY.equals(name) || RULE.equals(name);
    }

    private static boolean pair(String line, boolean stanzaOpen) {
        int assign = line.indexOf(ASSIGN);
        return assign > 0 && stanzaOpen && isKey(line.substring(0, assign).strip());
    }

    private static boolean isKey(CharSequence candidate) {
        return !candidate.isEmpty() && candidate.chars().noneMatch(Character::isWhitespace);
    }

    private static String summary(int offending, long meaningful, int line, String text) {
        return offending + " of " + meaningful
            + " meaningful line(s) are not valid configuration; first at line " + line + ": " + text;
    }
}
