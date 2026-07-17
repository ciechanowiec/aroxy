package eu.ciechanowiec.aroxy.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * The dependency-freshness entry, run under the full profile through the java launcher. It reads the
 * declared dependencies, asks Maven Central for the latest stable major of each, and throws when any
 * trails by the failing bound. Because the registry read can fail, the check is deliberately not a
 * test: an unreachable registry throws and fails the build (fail-closed). Date-versioned dependencies
 * with no comparable major are skipped.
 */
@UtilityClass
final class DependencyFreshnessEntry {

    static void main() {
        Path pom = Repository.root().resolve("pom.xml");
        List<String> violations = DeclaredDependencies.from(pom).stream()
            .filter(dependency -> DependencyFreshnessRules.hasComparableMajor(dependency.version()))
            .flatMap(dependency -> violation(dependency).stream())
            .toList();
        failIfAny(violations);
    }

    private static Optional<String> violation(DeclaredDependency dependency) {
        return DependencyFreshnessRules.violation(dependency, MavenCentral.latestMajor(dependency));
    }

    private static void failIfAny(Collection<String> violations) {
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "Dependencies trailing by the major-version bound:\n" + String.join("\n", violations)
            );
        }
    }
}
