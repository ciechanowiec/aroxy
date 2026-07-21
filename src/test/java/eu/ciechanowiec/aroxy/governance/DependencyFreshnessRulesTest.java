package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The freshness policy fails a dependency two or more majors behind and passes one that is current or
 * a single major behind. A date-versioned dependency uses its leading {@code 20**} year as the major,
 * so it follows the same bound.
 */
class DependencyFreshnessRulesTest {

    private static final DeclaredDependency PICOCLI = new DeclaredDependency("info.picocli", "picocli", "4.7.7");
    private static final DeclaredDependency JSON = new DeclaredDependency("org.json", "json", "20260719");
    private static final int ONE_YEAR_AHEAD = 2027;
    private static final int TWO_YEARS_AHEAD = 2028;

    @Test
    void failsADependencyTwoMajorsBehind() {
        assertFalse(DependencyFreshnessRules.violation(PICOCLI, 6).isEmpty());
    }

    @Test
    void passesADependencyOneMajorBehind() {
        assertTrue(DependencyFreshnessRules.violation(PICOCLI, 5).isEmpty());
    }

    @Test
    void passesACurrentDependency() {
        assertTrue(DependencyFreshnessRules.violation(PICOCLI, 4).isEmpty());
    }

    @Test
    void failsADateVersionedDependencyTwoYearsBehind() {
        assertTrue(DependencyFreshnessRules.hasComparableMajor(JSON.version()));
        assertFalse(DependencyFreshnessRules.violation(JSON, TWO_YEARS_AHEAD).isEmpty());
    }

    @Test
    void passesADateVersionedDependencyOneYearBehind() {
        assertTrue(DependencyFreshnessRules.violation(JSON, ONE_YEAR_AHEAD).isEmpty());
    }

    @Test
    void skipsAVersionWithoutAMajorOrLeadingYear() {
        DeclaredDependency named = new DeclaredDependency("example", "named", "release-2026");
        assertFalse(DependencyFreshnessRules.hasComparableMajor(named.version()));
    }

    @Test
    void comparesOnlyVersionsUsingTheDeclaredScheme() {
        assertTrue(DependencyFreshnessRules.sameScheme("20260719", "20280719"));
        assertFalse(DependencyFreshnessRules.sameScheme("20260719", "3.0.0"));
        assertFalse(DependencyFreshnessRules.sameScheme("3.0.0", "20280719"));
    }
}
