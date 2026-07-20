package eu.ciechanowiec.aroxy.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class CliOutputTest {

    private static final int PID = 42;

    @Test
    void rendersSemanticStatesAndAlignedDetailsWithoutControlCodes() {
        String text = render(false);
        assertTrue(text.contains("✓ Ready"), text);
        assertTrue(text.contains("● Running"), text);
        assertTrue(text.contains("○ Stopped"), text);
        assertTrue(text.contains("! Check configuration"), text);
        assertTrue(text.contains("✗ Failed"), text);
        assertTrue(text.contains("Heading"), text);
        assertTrue(text.contains("  PID          42"), text);
        assertTrue(text.contains("  Inspect the log"), text);
        assertFalse(text.contains("\033["), text);
    }

    @Test
    void addsAnsiStylingWhenEnabled() {
        assertTrue(render(true).contains("\033["), "an interactive terminal receives ANSI styling");
    }

    private static String render(boolean styled) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        CliOutput output = new CliOutput(new PrintStream(captured, true, UTF_8), styled);
        output.success("Ready");
        output.info("Running");
        output.inactive("Stopped");
        output.warning("Check configuration");
        output.error("Failed");
        output.heading("Heading");
        output.detail("PID", PID);
        output.note("Inspect the log");
        output.line("raw");
        return captured.toString(UTF_8);
    }
}
