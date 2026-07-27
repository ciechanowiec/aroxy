package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The entry file reader accepts a bare reference in either of the forms a reference takes, flags a
 * line that carries a rule alongside the reference, and separates an entry file that says too much
 * from one that says nothing.
 */
class EntryFileRulesTest {

    private static final String INSTRUCTIONS = "AGENTS.md";

    @Test
    void acceptsAnImportOfTheInstructionFile() {
        assertTrue(EntryFileRules.beyondTheReference("@AGENTS.md\n", INSTRUCTIONS).isEmpty());
    }

    @Test
    void acceptsThePlainLocationOfTheInstructionFile() {
        assertTrue(EntryFileRules.beyondTheReference("AGENTS.md\n", INSTRUCTIONS).isEmpty());
    }

    @Test
    void ignoresBlankLinesAroundTheReference() {
        assertTrue(EntryFileRules.beyondTheReference("\n@AGENTS.md\n\n", INSTRUCTIONS).isEmpty());
    }

    @Test
    void flagsARuleStatedAlongsideTheReference() {
        String content = "@AGENTS.md\nAlways run the verification command before pushing.\n";
        assertEquals(
            List.of("Always run the verification command before pushing."),
            EntryFileRules.beyondTheReference(content, INSTRUCTIONS),
            "a rule below the reference is a second home for the rules"
        );
    }

    @Test
    void flagsALineThatNamesTheInstructionFileAndThenStatesARule() {
        String content = "See AGENTS.md, and never use tabs.\n";
        assertEquals(
            1, EntryFileRules.beyondTheReference(content, INSTRUCTIONS).size(),
            "naming the instruction file does not license the rest of the line"
        );
    }

    @Test
    void readsAnEntryFileThatPointsAtTheInstructionFile() {
        assertTrue(EntryFileRules.referencesInstructionFile("@AGENTS.md\n", INSTRUCTIONS));
    }

    @Test
    void readsAnEmptyEntryFileAsPointingNowhere() {
        String content = "\n";
        assertTrue(
            EntryFileRules.beyondTheReference(content, INSTRUCTIONS).isEmpty(),
            "an empty entry file states no rule"
        );
        assertFalse(
            EntryFileRules.referencesInstructionFile(content, INSTRUCTIONS),
            "and states no reference either, which the second reading is for"
        );
    }
}
