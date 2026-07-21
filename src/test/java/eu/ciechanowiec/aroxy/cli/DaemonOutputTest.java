package eu.ciechanowiec.aroxy.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.daemon.Home;
import eu.ciechanowiec.aroxy.daemon.PidFile;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaemonOutputTest {

    @Test
    @SneakyThrows
    void rendersReadyAndRunningDetails(@TempDir Path directory) {
        Home home = new Home(directory);
        new PidFile(home.pidFile()).write(ProcessHandle.current().pid());
        Files.writeString(
            home.runtimeFile(),
            """
                start\t1
                generation\t1
                config.mask_secrets\ttrue
                config.rule.0\taws_key
                gateway\tClaude Code\t127.0.0.1:8788\thttps://api.example
                gateway\tSecure endpoint\thttps://127.0.0.1:8789\thttps://codex.example
                """
        );
        String text = render(output -> readyAndRunning(output, home));
        assertTrue(text.contains("✓ Daemon started"), text);
        assertTrue(text.contains("● Daemon already running"), text);
        assertTrue(text.contains("PID"), text);
        assertTrue(text.contains("http://127.0.0.1:8788"), text);
        assertTrue(text.contains("https://127.0.0.1:8789"), text);
    }

    @Test
    void rendersFailureWithSafetyStateAndLog(@TempDir Path directory) {
        Home home = new Home(directory);
        String text = render(output -> output.failed(home, "start"));
        assertTrue(text.contains("✗ Daemon failed to start"), text);
        assertTrue(text.contains("No proxy is running"), text);
        assertTrue(text.contains("daemon-err.log"), text);
    }

    private static void readyAndRunning(DaemonOutput output, Home home) {
        output.ready(home, "started");
        output.alreadyRunning(home);
    }

    private static String render(Consumer<DaemonOutput> action) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        action.accept(new DaemonOutput(new CliOutput(new PrintStream(captured, true, UTF_8))));
        return captured.toString(UTF_8);
    }
}
