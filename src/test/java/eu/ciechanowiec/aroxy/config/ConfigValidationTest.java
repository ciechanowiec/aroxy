package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ConfigFile} rejects files that are not in the stanza grammar - a Markdown or
 * prose document such as {@code CLAUDE.md} - instead of silently ingesting them as an empty
 * configuration, while still accepting a file that is empty or only comments (an emptied
 * {@code default.conf} is a legitimate no-op).
 */
class ConfigValidationTest {

    private static final String MARKDOWN = """
        # CLAUDE.md - aroxy

        aroxy is a local proxy between Claude Code and the Claude API.
        It obfuscates secrets before they leave the machine.
        """;

    @Test
    void rejectsMarkdownDocumentReportingWholeFile() {
        InvalidConfigException rejected = assertThrows(
            InvalidConfigException.class,
            () -> ConfigFile.parse("CLAUDE.md", MARKDOWN)
        );
        assertTrue(rejected.getMessage().contains("CLAUDE.md"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("not valid configuration"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("2 of 2"), rejected.getMessage());
    }

    @Test
    void reportsASingleStrayLineInAnOtherwiseValidConfig() {
        String text = "[rule]\nid = x\nplaceholder = X\npattern = abc\nthis is a stray prose line\n";
        InvalidConfigException rejected = assertThrows(
            InvalidConfigException.class,
            () -> ConfigFile.parse("typo", text)
        );
        assertTrue(rejected.getMessage().contains("1 of 5"), rejected.getMessage());
        assertTrue(rejected.getMessage().contains("line 5"), rejected.getMessage());
    }

    @Test
    void reportsTheFirstOffendingLineTextNotALaterOne() {
        String text = "[rule]\nid = x\nplaceholder = COMPANY\npattern = abc\nthis stray line is first\nvalue = tail\n";
        InvalidConfigException rejected = assertThrows(
            InvalidConfigException.class,
            () -> ConfigFile.parse("typo", text)
        );
        assertTrue(
            rejected.getMessage().contains("this stray line is first"),
            rejected.getMessage()
        );
    }

    @Test
    void rejectsKeyValuePairBeforeAnyStanza() {
        assertThrows(InvalidConfigException.class, () -> ConfigFile.parse("stray", "listen = 127.0.0.1:8788\n"));
    }

    @Test
    void rejectsUnknownStanza() {
        assertThrows(InvalidConfigException.class, () -> ConfigFile.parse("unknown", "[server]\nlisten = x\n"));
    }

    @Test
    void rejectsProseInsideAStanza() {
        assertThrows(
            InvalidConfigException.class,
            () -> ConfigFile.parse("mixed", "[rule]\nthis line is prose with no assignment\n")
        );
    }

    @Test
    void rejectsProseWithAnEqualsSignInsideAnOpenStanza() {
        String text = "[gateway]\nlisten = 127.0.0.1:8788\nThe API key = sk-12345 must stay secret\n";
        InvalidConfigException rejected = assertThrows(
            InvalidConfigException.class,
            () -> ConfigFile.parse("mixed", text)
        );
        assertTrue(rejected.getMessage().contains("line 3"), rejected.getMessage());
    }

    @Test
    void acceptsCommentsAndBlankLinesOnly() {
        assertDoesNotThrow(() -> ConfigFile.parse("emptied", "# only comments\n\n   \n# nothing else\n"));
    }

    @Test
    void acceptsValidStanzaFile() {
        String text = "[gateway]\nlisten = 127.0.0.1:8788\n[rule]\nid = x\nplaceholder = COMPANY\npattern = abc\n";
        assertDoesNotThrow(() -> ConfigFile.parse("valid", text));
    }
}
