package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompletionTest {

    @Test
    void generateCompletionEmitsScriptNamingSubcommands() {
        CliCapture.Result result = CliCapture.run("generate-completion");
        assertEquals(0, result.exit());
        String script = result.out();
        assertTrue(script.length() > 100, "completion script should be non-empty");
        assertTrue(script.contains("start"), script);
        assertTrue(script.contains("status"), script);
        assertTrue(script.contains("config"), script);
    }
}
