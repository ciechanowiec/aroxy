package eu.ciechanowiec.aroxy.governance;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Parses a raw commit message into a {@link CommitMessage}. Comment lines are dropped first, as git's
 * own cleanup does, then the header is the first line and the body is everything after it, stripped.
 * Both the whole-history reader and the commit-message hook entry parse through this one seam so they
 * agree on what a header and a body are: the hook sees the message before git has cleaned it, so
 * without dropping the comments here the template git appends would count as a body and the rule that
 * a non-trivial change carries one could never fire.
 */
@UtilityClass
final class CommitMessages {

    private static final String NEWLINE = "\n";
    private static final String COMMENT = "#";

    static CommitMessage parse(String raw) {
        String normalized = withoutComments(raw).strip();
        int newline = normalized.indexOf(NEWLINE);
        String header = newline < 0 ? normalized : normalized.substring(0, newline);
        String body = newline < 0 ? "" : normalized.substring(newline + 1).strip();
        return new CommitMessage(header, body);
    }

    private static String withoutComments(String raw) {
        return raw.lines()
            .filter(line -> !line.startsWith(COMMENT))
            .collect(Collectors.joining(NEWLINE));
    }
}
