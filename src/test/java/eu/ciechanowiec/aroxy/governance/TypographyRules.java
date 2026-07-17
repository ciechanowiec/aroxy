package eu.ciechanowiec.aroxy.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Scans already-decoded text for the typographic code points the guideline bans: the em dash, the en
 * dash, the single-character ellipsis, and the typographic (curly) quotation marks. The plain ASCII
 * equivalents (the hyphen, three periods, and the straight quotation mark) stand in their place. The
 * scan takes decoded text and never touches the filesystem, so it stays a pure, unit-testable rule.
 */
@UtilityClass
final class TypographyRules {

    static final int EM_DASH = 0x2014;
    static final int EN_DASH = 0x2013;
    static final int ELLIPSIS = 0x2026;
    static final int LEFT_SINGLE_QUOTE = 0x2018;
    static final int RIGHT_SINGLE_QUOTE = 0x2019;
    static final int LEFT_DOUBLE_QUOTE = 0x201C;
    static final int RIGHT_DOUBLE_QUOTE = 0x201D;
    static final int LOW_DOUBLE_QUOTE = 0x201E;
    private static final Set<Integer> BANNED = Set.of(
        EM_DASH, EN_DASH, ELLIPSIS, LEFT_SINGLE_QUOTE, RIGHT_SINGLE_QUOTE,
        LEFT_DOUBLE_QUOTE, RIGHT_DOUBLE_QUOTE, LOW_DOUBLE_QUOTE
    );
    private static final String NEWLINE = "\n";

    static List<TypographyViolation> findViolations(String content) {
        String[] lines = content.split(NEWLINE, -1);
        return IntStream.range(0, lines.length)
            .boxed()
            .flatMap(index -> violationsInLine(lines[index], index + 1).stream())
            .toList();
    }

    static boolean isBanned(int codePoint) {
        return BANNED.contains(codePoint);
    }

    private static List<TypographyViolation> violationsInLine(String line, int lineNumber) {
        Collection<TypographyViolation> found = new ArrayList<>();
        int column = 1;
        int index = 0;
        int length = line.length();
        while (index < length) {
            int codePoint = line.codePointAt(index);
            appendIfBanned(found, codePoint, lineNumber, column);
            column++;
            index += Character.charCount(codePoint);
        }
        return List.copyOf(found);
    }

    private static void appendIfBanned(
        Collection<TypographyViolation> found, int codePoint, int lineNumber, int column
    ) {
        if (isBanned(codePoint)) {
            found.add(new TypographyViolation(lineNumber, column, codePoint));
        }
    }
}
