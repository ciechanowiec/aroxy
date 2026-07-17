package eu.ciechanowiec.aroxy.governance;

import java.util.Optional;
import java.util.OptionalInt;
import lombok.experimental.UtilityClass;

/**
 * The dependency-freshness policy as a pure function: a declared dependency fails when its major
 * version trails the latest stable major by the declared bound or more. A version whose scheme carries
 * no comparable major (a date-stamped release such as {@code 20260522}) has no meaningful major gap and
 * is left out of the check.
 */
@UtilityClass
final class DependencyFreshnessRules {

    private static final int MAJOR_FAIL_THRESHOLD = 2;
    private static final char SEPARATOR = '.';

    static Optional<String> violation(DeclaredDependency declared, int latestMajor) {
        return major(declared.version())
            .stream()
            .boxed()
            .filter(declaredMajor -> latestMajor - declaredMajor >= MAJOR_FAIL_THRESHOLD)
            .map(declaredMajor -> render(declared, declaredMajor, latestMajor))
            .findFirst();
    }

    static boolean hasComparableMajor(String version) {
        return version.indexOf(SEPARATOR) > 0;
    }

    static OptionalInt major(String version) {
        if (!hasComparableMajor(version)) {
            return OptionalInt.empty();
        }
        String head = version.substring(0, version.indexOf(SEPARATOR));
        boolean numeric = !head.isEmpty() && head.chars().allMatch(Character::isDigit);
        return numeric ? OptionalInt.of(Integer.parseInt(head)) : OptionalInt.empty();
    }

    private static String render(DeclaredDependency declared, int declaredMajor, int latestMajor) {
        return "%s:%s is at major %d but the latest stable major is %d".formatted(
            declared.groupId(), declared.artifactId(), declaredMajor, latestMajor
        );
    }
}
