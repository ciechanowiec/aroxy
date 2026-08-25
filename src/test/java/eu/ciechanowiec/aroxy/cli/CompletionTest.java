package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompletionTest {

    private static final int SHORTEST_USEFUL_SCRIPT = 100;

    @Test
    void generateCompletionEmitsScriptNamingSubcommands() {
        CliCapture.Result result = CliCapture.run("generate-completion");
        assertEquals(0, result.exit());
        String script = result.out();
        assertTrue(
            script.length() > SHORTEST_USEFUL_SCRIPT,
            "a completion script shorter than this carries no subcommands to complete"
        );
        assertTrue(script.contains("start"), script);
        assertTrue(script.contains("status"), script);
        assertTrue(script.contains("config"), script);
    }
}
