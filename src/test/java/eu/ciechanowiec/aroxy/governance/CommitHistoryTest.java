package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Every commit reachable from the history, back to and including the first commit, satisfies the
 * commit-message policy. This walks the whole history, so it is tagged {@code full} and runs only
 * under the full verification profile.
 */
@Tag(
    "full"
)
class CommitHistoryTest {

    @Test
    void everyCommitFollowsTheConvention() {
        List<String> failures = CommitLog.commits(Repository.root()).stream()
            .flatMap(commit -> annotate(commit).stream())
            .toList();
        assertTrue(
            failures.isEmpty(),
            () -> "Commit messages that break the policy:\n" + String.join("\n", failures)
        );
    }

    private static List<String> annotate(Commit commit) {
        return CommitMessageRules.validate(commit.message(), commit.stat()).stream()
            .map(violation -> commit.sha() + ": " + violation)
            .toList();
    }
}
