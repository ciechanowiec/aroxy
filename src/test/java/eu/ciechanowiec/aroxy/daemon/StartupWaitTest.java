package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the daemon startup handshake: only a live recorded process with a listener-bearing snapshot
 * published by the current launch is ready. A stale snapshot, dead child, or snapshot written before
 * listeners bind must fail closed.
 */
class StartupWaitTest {

    private static final long LAUNCHED_AT = 200L;

    @Test
    @SneakyThrows
    void freshSnapshotFromLiveProcessIsReady(@TempDir Path directory) {
        PidFile pid = livePid(directory);
        Path runtime = directory.resolve("runtime");
        snapshot(LAUNCHED_AT, listeners()).writeTo(runtime);
        assertTrue(ready(pid, runtime), "a fresh snapshot proves the listeners bound");
    }

    @Test
    @SneakyThrows
    void staleSnapshotCannotMakeANewLaunchLookReady(@TempDir Path directory) {
        PidFile pid = livePid(directory);
        Path runtime = directory.resolve("runtime");
        snapshot(LAUNCHED_AT - 1, listeners()).writeTo(runtime);
        assertFalse(ready(pid, runtime), "a previous daemon's snapshot is not a readiness signal");
    }

    @Test
    @SneakyThrows
    void snapshotWithoutBoundListenersIsNotReady(@TempDir Path directory) {
        PidFile pid = livePid(directory);
        Path runtime = directory.resolve("runtime");
        snapshot(LAUNCHED_AT, List.of()).writeTo(runtime);
        assertFalse(ready(pid, runtime), "startup completes only after listeners bind");
    }

    @Test
    @SneakyThrows
    void deadRecordedProcessCannotBeReady(@TempDir Path directory) {
        PidFile pid = new PidFile(directory.resolve("aroxy.pid"));
        pid.write(Long.MAX_VALUE);
        Path runtime = directory.resolve("runtime");
        snapshot(LAUNCHED_AT, listeners()).writeTo(runtime);
        assertFalse(ready(pid, runtime), "a snapshot cannot revive a child that already exited");
    }

    @Test
    @SneakyThrows
    void differentRecordedProcessCannotSatisfyLaunch(@TempDir Path directory) {
        PidFile pid = livePid(directory);
        Path runtime = directory.resolve("runtime");
        snapshot(LAUNCHED_AT, listeners()).writeTo(runtime);
        assertFalse(
            StartupWait.untilReady(
                pid, runtime,
                new StartupWait.Attempt(LAUNCHED_AT, Long.MAX_VALUE, new StartupWait.Poll(1, 0L))
            ),
            "readiness belongs to the exact child that was launched"
        );
    }

    private static PidFile livePid(Path directory) {
        PidFile pid = new PidFile(directory.resolve("aroxy.pid"));
        pid.write(ProcessHandle.current().pid());
        return pid;
    }

    @SneakyThrows
    private static boolean ready(PidFile pid, Path runtime) {
        long expected = pid.read().orElse(Long.MAX_VALUE);
        return StartupWait.untilReady(
            pid, runtime, new StartupWait.Attempt(LAUNCHED_AT, expected, new StartupWait.Poll(1, 0L))
        );
    }

    private static RuntimeSnapshot snapshot(long start, List<GatewayAddress> gateways) {
        return new RuntimeSnapshot(gateways, start, 1L, List.of(), new RuntimeConfig(true, List.of()));
    }

    private static List<GatewayAddress> listeners() {
        return List.of(new GatewayAddress("Codex CLI", "127.0.0.1:8789", "https://example.test"));
    }
}
