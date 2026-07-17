package eu.ciechanowiec.aroxy.governance;

import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Parses the {@code git} numstat output (one {@code added\tdeleted\tpath} row per file) into a
 * {@link DiffStat}. A binary file, which git reports as {@code -\t-\tpath}, counts as a touched file
 * contributing zero changed lines.
 */
@UtilityClass
final class DiffStats {

    private static final String TAB = "\t";
    private static final int COLUMNS = 3;

    static DiffStat parse(String numstat) {
        List<String[]> rows = numstat.lines()
            .filter(line -> !line.isBlank())
            .map(line -> line.split(TAB))
            .filter(columns -> columns.length >= COLUMNS)
            .toList();
        int lines = rows.stream().mapToInt(DiffStats::changedLines).sum();
        return new DiffStat(rows.size(), lines);
    }

    private static int changedLines(String... columns) {
        return numberOrZero(columns[0]) + numberOrZero(columns[1]);
    }

    private static int numberOrZero(String token) {
        boolean numeric = !token.isEmpty() && token.chars().allMatch(Character::isDigit);
        return numeric ? Integer.parseInt(token) : 0;
    }
}
