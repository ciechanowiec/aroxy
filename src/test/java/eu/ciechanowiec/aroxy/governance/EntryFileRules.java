package eu.ciechanowiec.aroxy.governance;

import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Reads an entry file, which is a file an agent tool loads for instructions under a fixed name of its
 * own. An entry file points at the root instruction file and carries no rule of its own, so the rules
 * have one home whichever tool reads them.
 *
 * <p>A line states something beyond the reference when removing the instruction file's name from it
 * leaves a letter or a digit behind. That reading accepts the forms a reference actually takes, an
 * import marker or a bare path, and rejects prose, because prose cannot be written without letters.
 * The alternative, asking whether a line merely mentions the name, would pass a line that names the
 * instruction file and then goes on to state a rule.
 */
@UtilityClass
final class EntryFileRules {

    /**
     * The lines of {@code content} that state something besides the reference to
     * {@code instructionFile}.
     *
     * @param content         the entry file's text
     * @param instructionFile the name of the instruction file the entry file points at
     * @return the offending lines, in the order the entry file holds them
     */
    static List<String> beyondTheReference(String content, CharSequence instructionFile) {
        return content.lines()
            .filter(line -> !line.isBlank())
            .filter(line -> statesMore(line, instructionFile))
            .toList();
    }

    /**
     * Whether {@code content} points at {@code instructionFile} at all. An entry file that states no
     * rule and names nothing either is empty of instruction, which reads as compliant to
     * {@link #beyondTheReference(String, CharSequence)} while leaving the tool that opens it with nothing.
     *
     * @param content         the entry file's text
     * @param instructionFile the name of the instruction file the entry file points at
     * @return whether the reference is present
     */
    static boolean referencesInstructionFile(String content, CharSequence instructionFile) {
        return content.contains(instructionFile);
    }

    private static boolean statesMore(String line, CharSequence instructionFile) {
        String remainder = line.replace(instructionFile, "");
        return remainder.chars().anyMatch(Character::isLetterOrDigit);
    }
}
