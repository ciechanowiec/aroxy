package eu.ciechanowiec.aroxy.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import org.junit.jupiter.api.Test;

class AroxyTest {

    @Test
    void printsHelp() {
        int exitCode = Aroxy.execute("--help");
        assertEquals(0, exitCode);
    }

    /**
     * The banner is the first thing a user of any agent reads, so it must not advertise one of them.
     * It named Claude Code alone for as long as Claude Code was the only client, and nothing caught
     * that once a second client arrived, because the help test asserted only the exit code. Checking
     * it against the {@link ClientProfile} labels rather than against a fixed string means a client
     * registered later is covered without anyone remembering to come back here.
     */
    @Test
    void theHelpBannerNamesNoSingleAgent() {
        String help = CliCapture.run("--help").out();
        assertTrue(help.contains("aroxy"), help);
        ClientRegistry.profiles().forEach(
            profile -> assertFalse(
                help.contains(profile.displayName()),
                "the banner names " + profile.displayName() + " but serves every agent equally: " + help
            )
        );
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
