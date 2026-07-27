package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Every entry file this repository ships holds a reference to the root instruction file and nothing
 * else. The names below are the declaration the rule asks for: an agent tool this repository is opened
 * to gets a line here, and a file no line names is not an entry file. This is fast and reads no
 * network, so it runs on every build.
 */
class EntryFileTest {

    private static final String INSTRUCTIONS = "AGENTS.md";
    private static final List<String> ENTRY_FILES = List.of("CLAUDE.md");

    @Test
    void everyEntryFileStatesNothingBesidesTheReference() {
        Path root = Repository.root();
        List<String> stated = ENTRY_FILES.stream()
            .flatMap(
                name -> EntryFileRules.beyondTheReference(read(root, name), INSTRUCTIONS).stream()
                    .map(line -> name + ": " + line)
            )
            .toList();
        assertTrue(
            stated.isEmpty(),
            () -> "An entry file states what only " + INSTRUCTIONS + " may state:\n" + String.join("\n", stated)
        );
    }

    @Test
    void everyEntryFileReferencesTheInstructionFile() {
        Path root = Repository.root();
        List<String> silent = ENTRY_FILES.stream()
            .filter(name -> !EntryFileRules.referencesInstructionFile(read(root, name), INSTRUCTIONS))
            .toList();
        assertTrue(
            silent.isEmpty(),
            () -> "An entry file does not point at " + INSTRUCTIONS + ":\n" + String.join("\n", silent)
        );
    }

    @Test
    void theInstructionFileIsPresent() {
        Optional<String> content = Repository.readText(Repository.root().resolve(INSTRUCTIONS));
        assertTrue(
            content.filter(text -> !text.isBlank()).isPresent(),
            INSTRUCTIONS + " is where every rule lives, so an entry file pointing at it must resolve"
        );
    }

    private static String read(Path root, String name) {
        return Repository.readText(root.resolve(name))
            .orElseThrow(() -> new IllegalStateException("Declared entry file is missing: " + name));
    }
}
