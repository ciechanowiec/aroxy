package eu.ciechanowiec.aroxy.daemon;

import java.nio.file.Path;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * The naming and JVM-flag contract shared between the {@link Launcher} that spawns the background
 * daemon and the CLI entry that runs it. {@link #NAME} is the hidden subcommand the launcher passes;
 * {@link #loggingFlags(Home)} are the {@code -D} overrides that route tinylog to a rolling
 * {@code aroxy.log} under {@link Home}{@code /logs} without the CLI process needing a compile-time logging
 * dependency.
 */
@UtilityClass
public final class DaemonProcess {

    /**
     * The hidden subcommand name the launcher passes to the background JVM.
     */
    public static final String NAME = "__daemon";
    private static final String PREFIX = "-Dtinylog.";

    static List<String> loggingFlags(Home home) {
        Path log = home.appLog();
        return List.of(
            PREFIX + "writerFile=rolling file",
            PREFIX + "writerFile.level=info",
            PREFIX + "writerFile.file=" + log,
            PREFIX + "writerFile.format=[{date: yyyy-MM-dd HH:mm:ss.SSS}] [{level}] {message}",
            PREFIX + "writerFile.policies=startup, daily, size: 4mb",
            PREFIX + "writerFile.backups=5",
            PREFIX + "writerFile.charset=UTF-8"
        );
    }
}
