package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every type a production Javadoc comment names is reached by a link rather than left as prose. The
 * name of a type is the one part of a comment a reader can follow to its definition, and the Javadoc
 * tool already fails the build on a link that does not resolve, so a resolvable name left unlinked is
 * the one documentation defect nothing else here would catch. This is fast and reads no network, so it
 * runs on every build.
 */
class JavadocLinkTest {

    private static final Path SOURCES = Path.of("src/main/java");

    @Test
    void everyTypeNamedInProductionJavadocIsLinked() {
        Path root = Repository.root();
        List<Path> sources = productionSources(root);
        JavadocScope scope = JavadocScope.over(sources);
        List<String> offences = sources.stream()
            .flatMap(source -> unlinkedIn(root, source, scope))
            .toList();
        assertTrue(
            offences.isEmpty(),
            () -> "Javadoc names types it does not link, though they resolve from that file:\n"
                + String.join("\n", offences)
        );
    }

    private static Stream<String> unlinkedIn(Path root, Path source, JavadocScope scope) {
        return Repository.readText(source).stream().flatMap(text -> report(root, source, text, scope));
    }

    private static Stream<String> report(Path root, Path source, CharSequence text, JavadocScope scope) {
        Predicate<String> resolves = scope.of(source, text);
        return JavadocLinkRules.unlinked(text, resolves).stream()
            .map(name -> "    %s: %s".formatted(root.relativize(source), name));
    }

    private static List<Path> productionSources(Path root) {
        return Repository.trackedFiles(root).stream()
            .filter(file -> file.startsWith(root.resolve(SOURCES)))
            .filter(file -> file.getFileName().toString().endsWith(".java"))
            .toList();
    }
}
