package eu.ciechanowiec.aroxy.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The commit-message hook entry. Invoked as {@code java ... CommitMessageEntry <message-file>}, it
 * parses the pending message, sizes the staged change to decide whether a body is required, and throws
 * when the message breaks the policy. An uncaught throw makes the launcher exit non-zero, which the
 * hook turns into a rejected commit. It reads only the JDK and the governance classes, so the hook
 * needs nothing beyond the compiled test classes on its classpath.
 */
@UtilityClass
final class CommitMessageEntry {

    static void main(String... arguments) {
        Path root = Repository.root();
        Path file = messageFile(arguments);
        CommitMessage message = CommitMessages.parse(read(file));
        DiffStat stat = DiffStats.parse(stagedNumstat(root));
        failIfAny(CommitMessageRules.validate(message, stat));
    }

    private static Path messageFile(String... arguments) {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("Usage: CommitMessageEntry <commit-message-file>");
        }
        return Path.of(arguments[0]);
    }

    private static String stagedNumstat(Path root) {
        return GitPlumbing.run(root, List.of("diff", "--cached", "--numstat"));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read commit message file " + file, exception);
        }
    }

    private static void failIfAny(Collection<String> violations) {
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "Commit message violates policy:\n" + String.join("\n", violations)
            );
        }
    }
}
