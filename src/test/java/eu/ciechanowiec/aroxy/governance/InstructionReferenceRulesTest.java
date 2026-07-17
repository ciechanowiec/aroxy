package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The reference resolver flags a missing repository path or type name, accepts an existing one, and
 * ignores tokens that are neither: identifiers, hosts, runtime paths outside the working tree, and
 * anything inside a fenced code block.
 */
class InstructionReferenceRulesTest {

    private static final Set<String> DIRECTORIES = Set.of("src", ".github");
    private static final String FENCE = "```";

    @Test
    void flagsAMissingRepositoryPath() {
        String content = "See `src/main/java/Missing.java` for details.";
        List<String> unresolved = InstructionReferenceRules.unresolved(content, DIRECTORIES, path -> false);
        assertEquals(1, unresolved.size());
    }

    @Test
    void acceptsAnExistingRepositoryPath() {
        String content = "See `src/main/java/Present.java`.";
        assertTrue(InstructionReferenceRules.unresolved(content, DIRECTORIES, path -> true).isEmpty());
    }

    @Test
    void ignoresIdentifiersAndHosts() {
        String content = "Point `ANTHROPIC_BASE_URL` at `127.0.0.1:8788` and read `api.anthropic.com`.";
        assertTrue(InstructionReferenceRules.unresolved(content, DIRECTORIES, path -> false).isEmpty());
    }

    @Test
    void ignoresARuntimePathOutsideTheRepository() {
        String content = "State lives in `~/.aroxy/vault.tsv` at runtime.";
        assertTrue(InstructionReferenceRules.unresolved(content, DIRECTORIES, path -> false).isEmpty());
    }

    @Test
    void readsReferencesThatFollowAFencedBlock() {
        String content = FENCE + "\na diagram `not a reference`\n" + FENCE + "\nSee `src/main/java/Missing.java`.";
        List<String> unresolved = InstructionReferenceRules.unresolved(content, DIRECTORIES, path -> false);
        assertEquals(
            List.of("src/main/java/Missing.java"), unresolved,
            "a fence pairs its own backticks, so what follows it must still be read"
        );
    }

    @Test
    void flagsAMissingTypeName() {
        String content = "The engine composes `RuleLabel` and `SecretMasker`.";
        List<String> unresolved = InstructionReferenceRules.unresolvedTypes(content, "RuleLabel"::equals);
        assertEquals(List.of("SecretMasker"), unresolved, "only the name nothing accounts for is flagged");
    }

    @Test
    void ignoresProseAndSingleWordTokensWhenReadingTypeNames() {
        String content = "Run `mvn clean package -Pfull`, read `main`, keep `Optional` in mind.";
        assertTrue(
            InstructionReferenceRules.unresolvedTypes(content, name -> false).isEmpty(),
            "only multi-word CamelCase is a type name, so prose cannot be mistaken for one"
        );
    }
}
