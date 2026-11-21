package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The published stats file round-trips every counter, reads as absent when there is nothing to read,
 * and tolerates a field it cannot parse rather than hiding the rest.
 */
class TrafficFileTest {

    private static final TrafficTotals TOTALS = new TrafficTotals(412L, 57L, 203L, 18_400_000L, 96_100_000L, 99L);

    @Test
    void roundTripsEveryCounter(@TempDir Path base) {
        Path file = base.resolve("stats");
        StateFile.write(file, TrafficFile.lines(TOTALS));
        assertEquals(Optional.of(TOTALS), TrafficFile.read(file));
    }

    @Test
    void readsNothingWhenTheFileIsAbsent(@TempDir Path base) {
        assertTrue(TrafficFile.read(base.resolve("stats")).isEmpty());
    }

    @Test
    @SneakyThrows
    void readsNothingFromAFileThatIsNotAStatsFile(@TempDir Path base) {
        Path file = Files.writeString(base.resolve("stats"), "start\t1\n");
        assertTrue(TrafficFile.read(file).isEmpty());
    }

    @Test
    @SneakyThrows
    void toleratesAGarbledCounter(@TempDir Path base) {
        Path file = Files.writeString(base.resolve("stats"), "requests\t7\nsent\tlots\nnoise\n");
        TrafficTotals read = TrafficFile.read(file).orElseThrow();
        assertEquals(new TrafficTotals(7L, 0L, 0L, 0L, 0L, 0L), read);
    }
}
