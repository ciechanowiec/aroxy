package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherTest {

    private static final String JAR = "/opt/aroxy/aroxy.jar";

    @TempDir
    private Path base;

    @Test
    void buildsACommandEndingWithTheDaemonEntry() {
        Launcher launcher = new Launcher(new Home(this.base));
        List<String> command = launcher.buildCommand(JAR);
        assertTrue(command.contains("-jar"), command.toString());
        assertEquals(DaemonProcess.NAME, command.getLast(), command.toString());
    }

    @Test
    void commandRoutesLoggingToTheHomeLogDirectory() {
        Home home = new Home(this.base);
        List<String> command = new Launcher(home).buildCommand(JAR);
        String log = home.appLog().toString();
        assertTrue(command.stream().anyMatch(part -> part.contains(log)), command.toString());
        assertTrue(command.stream().anyMatch(part -> part.contains("rolling file")), command.toString());
    }
}
