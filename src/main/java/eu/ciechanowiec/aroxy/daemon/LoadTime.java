package eu.ciechanowiec.aroxy.daemon;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.experimental.UtilityClass;

/**
 * Formats an epoch-millisecond load time for display. A non-positive timestamp (a file present but
 * not yet loaded by a running daemon) renders as a dash rather than the 1970 epoch.
 */
@UtilityClass
final class LoadTime {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String UNLOADED = "-";
    private static final long UNSTAMPED = 0L;

    static String iso(long millis) {
        if (millis <= UNSTAMPED) {
            return UNLOADED;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(ISO);
    }
}
