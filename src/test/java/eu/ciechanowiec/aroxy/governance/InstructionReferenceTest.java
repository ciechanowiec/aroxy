package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every repository path and every type name that the root instruction file references resolves to
 * something the repository holds. This is fast and reads no network, so it runs on every build.
 */
class InstructionReferenceTest {

    private static final Path INSTRUCTIONS = Path.of("CLAUDE.md");
    private static final Path ANALYSIS = Path.of("src/main/resources/static_code_analysis");
    private static final Path QODANA = Path.of("qodana/profile.xml");
    private static final Path SOURCES = Path.of("src");

    @Test
    void everyRepositoryPathInTheInstructionFileResolves() {
        Path root = Repository.root();
        String content = instructions(root);
        List<String> unresolved = InstructionReferenceRules.unresolved(
            content, Repository.topLevelDirectories(root), token -> Files.exists(root.resolve(token))
        );
        assertTrue(
            unresolved.isEmpty(),
            () -> "Broken repository references in CLAUDE.md:\n" + String.join("\n", unresolved)
        );
    }

    /**
     * A type name the instructions mention must be a type this repository declares, a rule or
     * inspection the analysis configuration names, or a type the sources import. Those three are where
     * every name the instructions can honestly use comes from, so no allowlist is needed and a name
     * that survives only in the prose is reported.
     */
    @Test
    void everyTypeNameInTheInstructionFileResolves() {
        Path root = Repository.root();
        Set<String> declared = declaredTypes(root);
        String analysis = analysisConfiguration(root);
        Set<String> imported = importedTypes(root);
        List<String> unresolved = InstructionReferenceRules.unresolvedTypes(
            instructions(root),
            name -> declared.contains(name) || analysis.contains(name) || imported.contains(name)
        );
        assertTrue(
            unresolved.isEmpty(),
            () -> "CLAUDE.md names types that this repository does not have:\n" + String.join("\n", unresolved)
        );
    }

    private static String instructions(Path root) {
        return Repository.readText(root.resolve(INSTRUCTIONS)).orElseThrow();
    }

    private static Set<String> declaredTypes(Path root) {
        return javaFiles(root)
            .map(file -> file.getFileName().toString().replace(".java", ""))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> importedTypes(Path root) {
        return javaFiles(root)
            .map(Repository::readText)
            .flatMap(Optional::stream)
            .flatMap(String::lines)
            .filter(line -> line.startsWith("import "))
            .map(InstructionReferenceTest::simpleName)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String simpleName(String importLine) {
        String qualified = importLine.replace("import ", "").replace("static ", "").replace(";", "").strip();
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    private static Stream<Path> javaFiles(Path root) {
        return Repository.trackedFiles(root).stream()
            .filter(file -> file.startsWith(root.resolve(SOURCES)))
            .filter(file -> file.getFileName().toString().endsWith(".java"));
    }

    private static String analysisConfiguration(Path root) {
        return Repository.trackedFiles(root).stream()
            .filter(file -> file.startsWith(root.resolve(ANALYSIS)) || file.equals(root.resolve(QODANA)))
            .map(Repository::readText)
            .flatMap(Optional::stream)
            .collect(Collectors.joining("\n"));
    }
}
