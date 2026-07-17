package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AroxyTest {

    @Test
    void printsHelp() {
        int exitCode = Aroxy.execute("--help");
        assertEquals(0, exitCode);
    }

    @Test
    void printsVersion() {
        int exitCode = Aroxy.execute("--version");
        assertEquals(0, exitCode);
    }

    @Test
    void runsWithoutArguments() {
        int exitCode = Aroxy.execute();
        assertEquals(0, exitCode);
    }
}
