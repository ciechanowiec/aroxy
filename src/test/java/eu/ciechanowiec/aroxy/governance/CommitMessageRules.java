package eu.ciechanowiec.aroxy.governance;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The commit-message policy, expressed as a pure function over an already-parsed {@link CommitMessage}
 * and its {@link DiffStat}. It enforces the Conventional Commits header with a closed type list, a
 * subject of the right length with no trailing period and no junk word, a body for a non-trivial
 * change, and the absence of any marker that attributes the change to an AI agent. Merge and revert
 * headers keep their fixed git forms, so they are exempt from the header shape alone; the attribution
 * ban holds for every commit, because a fixed header form is no licence to name an agent in the body.
 */
@UtilityClass
final class CommitMessageRules {

    private static final Pattern HEADER = Pattern.compile(
        "^(feat|fix|docs|refactor|perf|test|build|ci|chore|revert)(\\([a-z0-9.-]+\\))?!?: .+$"
    );
    private static final String SEPARATOR = ": ";
    private static final int MIN_SUBJECT = 15;
    private static final int MAX_SUBJECT = 72;
    private static final int MAX_TRIVIAL_FILES = 2;
    private static final int MAX_TRIVIAL_LINES = 50;
    private static final List<String> JUNK_WORDS = List.of(
        "wip", "tmp", "temp", "misc", "stuff", "asdf", "fixup"
    );
    private static final String AGENTS = "(claude|anthropic|copilot|cursor|codex|chatgpt|gpt|gemini|devin|aider)";
    private static final List<Pattern> ATTRIBUTION = List.of(
        Pattern.compile("(?i)co-authored-by:.*" + AGENTS),
        Pattern.compile("(?i)generated\\s+(with|by)\\b.*" + AGENTS),
        Pattern.compile("(?i)\\b(claude|assistant|agent)[- ]?session(-id)?\\s*:"),
        Pattern.compile("(?i)claude\\.(ai|com)/claude-code")
    );

    static List<String> validate(CommitMessage message, DiffStat stat) {
        return Stream.concat(shapeViolations(message, stat), Stream.of(attributionViolation(message)))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Stream<Optional<String>> shapeViolations(CommitMessage message, DiffStat stat) {
        if (isExempt(message.header())) {
            return Stream.of();
        }
        return Stream.of(
            headerViolation(message.header()),
            subjectLengthViolation(message.header()),
            trailingPeriodViolation(message.header()),
            junkWordViolation(message.header()),
            bodyViolation(message, stat)
        );
    }

    private static boolean isExempt(String header) {
        return header.startsWith("Merge ") || header.startsWith("Revert \"");
    }

    private static Optional<String> headerViolation(CharSequence header) {
        boolean matches = HEADER.matcher(header).matches();
        return matches ? Optional.empty()
            : Optional.of("Header must be 'type(scope): subject' with a known type: " + header);
    }

    private static Optional<String> subjectLengthViolation(String header) {
        int length = subject(header).length();
        boolean withinRange = length >= MIN_SUBJECT && length <= MAX_SUBJECT;
        return withinRange ? Optional.empty()
            : Optional.of(
                "Subject length %d is outside %d..%d: %s"
                    .formatted(length, MIN_SUBJECT, MAX_SUBJECT, header)
            );
    }

    private static Optional<String> trailingPeriodViolation(String header) {
        String subject = subject(header);
        return subject.endsWith(".")
            ? Optional.of("Subject must not end with a period: " + subject)
            : Optional.empty();
    }

    private static Optional<String> junkWordViolation(String header) {
        String lowered = header.toLowerCase(Locale.ROOT);
        return JUNK_WORDS.stream()
            .filter(word -> containsWord(lowered, word))
            .findFirst()
            .map(word -> "Header contains a banned junk word '%s': %s".formatted(word, header));
    }

    private static Optional<String> bodyViolation(CommitMessage message, DiffStat stat) {
        boolean needsBody = stat.changedFiles() > MAX_TRIVIAL_FILES
            || stat.changedLines() > MAX_TRIVIAL_LINES;
        boolean missingBody = message.body().isBlank();
        return needsBody && missingBody
            ? Optional.of("A non-trivial change must carry a body explaining why it was made")
            : Optional.empty();
    }

    private static Optional<String> attributionViolation(CommitMessage message) {
        String text = message.header() + "\n" + message.body();
        return ATTRIBUTION.stream()
            .filter(pattern -> pattern.matcher(text).find())
            .findFirst()
            .map(pattern -> "Commit message attributes the change to an AI agent: " + pattern.pattern());
    }

    private static boolean containsWord(CharSequence text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private static String subject(String header) {
        int marker = header.indexOf(SEPARATOR);
        return marker < 0 ? header : header.substring(marker + SEPARATOR.length());
    }
}
