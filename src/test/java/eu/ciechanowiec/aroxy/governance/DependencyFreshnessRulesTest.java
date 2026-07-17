package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The freshness policy fails a dependency two or more majors behind, passes one that is current or a
 * single major behind, and skips a date-versioned release that has no comparable major.
 */
class DependencyFreshnessRulesTest {

    private static final DeclaredDependency PICOCLI = new DeclaredDependency("info.picocli", "picocli", "4.7.7");

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
    void skipsADateVersionedDependency() {
        DeclaredDependency json = new DeclaredDependency("org.json", "json", "20260522");
        assertFalse(DependencyFreshnessRules.hasComparableMajor(json.version()));
        assertTrue(DependencyFreshnessRules.violation(json, 4).isEmpty());
    }
}
