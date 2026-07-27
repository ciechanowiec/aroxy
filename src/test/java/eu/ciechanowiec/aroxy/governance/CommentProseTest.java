package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No comment in a tracked Java source joins two clauses with a semicolon, and no {@code @return} tag
 * carries a full stop. A comment is read under time pressure, so the sentence that survives is the short
 * one: a full stop where both clauses stand alone, a comma where the second is subordinate. A
 * {@code @return} is the exception in both directions, being a fragment that completes "returns ...":
 * it takes no full stop, and with none available a semicolon is what joins a second clause to it.
 *
 * <p>Main and test sources are held to this equally. A rule that reached only production Javadoc would
 * leave four fifths of the comments anyone reads outside it, which is a rule that says more than it
 * does. This is fast and reads no network, so it runs on every build.
 */
class CommentProseTest {

    private static final List<Path> SOURCES = List.of(Path.of("src/main/java"), Path.of("src/test/java"));

    @Test
    void commentProseJoinsClausesWithoutSemicolons() {
        Path root = Repository.root();
        List<String> offences = offences(root, CommentProseRules::semicolons);
        assertTrue(
            offences.isEmpty(),
            () -> "Comment prose uses a semicolon where a full stop or a comma reads shorter:\n"
                + String.join("\n", offences)
        );
    }

    @Test
    void returnTagsAreFragmentsRatherThanSentences() {
        Path root = Repository.root();
        List<String> offences = offences(root, CommentProseRules::returnPeriods);
        assertTrue(
            offences.isEmpty(),
            () -> "A @return completes \"returns ...\", so it takes no full stop:\n"
                + String.join("\n", offences)
        );
    }

    private static List<String> offences(Path root, ProseRule rule) {
        return javaSources(root).stream()
            .flatMap(source -> offencesIn(root, source, rule))
            .toList();
    }

    private static Stream<String> offencesIn(Path root, Path source, ProseRule rule) {
        return Repository.readText(source).stream()
            .flatMap(text -> rule.offences(text).stream())
            .map(line -> "    %s: %s".formatted(root.relativize(source), line));
    }

    private static List<Path> javaSources(Path root) {
        return Repository.trackedFiles(root).stream()
            .filter(file -> SOURCES.stream().anyMatch(source -> file.startsWith(root.resolve(source))))
            .filter(file -> file.getFileName().toString().endsWith(".java"))
            .toList();
    }

    @FunctionalInterface
    private interface ProseRule {

        List<String> offences(CharSequence source);
    }
}
