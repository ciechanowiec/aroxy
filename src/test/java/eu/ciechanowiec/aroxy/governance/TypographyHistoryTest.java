package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Every commit message in the whole history uses only plain ASCII typography. This walks the whole
 * history, so it is tagged {@code full} and runs only under the full verification profile.
 */
@Tag(
    "full"
)
class TypographyHistoryTest {

    @Test
    void commitMessagesUseOnlyPlainTypography() {
        List<String> violations = CommitLog.commits(Repository.root()).stream()
            .flatMap(commit -> messageViolations(commit).stream())
            .toList();
        assertTrue(
            violations.isEmpty(),
            () -> "Banned typography in commit messages:\n" + String.join("\n", violations)
        );
    }

    private static List<String> messageViolations(Commit commit) {
        String text = commit.message().header() + "\n" + commit.message().body();
        return TypographyRules.findViolations(text).stream()
            .map(violation -> "%s: U+%04X".formatted(commit.sha(), violation.codePoint()))
            .toList();
    }
}
