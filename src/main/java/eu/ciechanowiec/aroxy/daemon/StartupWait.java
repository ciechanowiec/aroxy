package eu.ciechanowiec.aroxy.daemon;

import java.nio.file.Path;
import lombok.experimental.UtilityClass;

/**
 * Performs the bounded readiness handshake for a newly spawned daemon. A process being alive is not
 * enough: readiness requires a runtime snapshot whose start time belongs to this launch. The daemon
 * publishes that snapshot only after every configured listener has bound, so a bind or configuration
 * failure can never be reported as a successful start.
 */
@UtilityClass
final class StartupWait {

    static final long TIMEOUT_SECONDS = 5L;
    private static final Poll DEFAULT = new Poll(50, 100L);

    // bounded readiness poll delegates to the testable overload below
    static boolean untilReady(
        PidFile pid, Path runtime, long launchedAt, long expectedPid
    ) throws InterruptedException {
        return untilReady(pid, runtime, new Attempt(launchedAt, expectedPid, DEFAULT));
    }

    // package seam keeps failure-path tests immediate rather than sleeping for the production timeout
    @SuppressWarnings(
        "BusyWait"
    )
    static boolean untilReady(PidFile pid, Path runtime, Attempt attempt) throws InterruptedException {
        int round = 0;
        while (waiting(pid, attempt, round)) {
            if (ready(pid, runtime, attempt)) {
                return true;
            }
            Thread.sleep(attempt.poll().pauseMillis());
            round++;
        }
        return false;
    }

    private static boolean waiting(PidFile pid, Attempt attempt, int round) {
        return round < attempt.poll().attempts() && expected(pid, attempt.expectedPid());
    }

    private static boolean ready(PidFile pid, Path runtime, Attempt attempt) {
        return fresh(runtime, attempt.launchedAt()) && expected(pid, attempt.expectedPid());
    }

    private static boolean expected(PidFile pid, long expectedPid) {
        return pid.read().filter(recorded -> recorded == expectedPid).isPresent() && pid.isRunning();
    }

    private static boolean fresh(Path runtime, long launchedAt) {
        return RuntimeSnapshot.read(runtime)
            .filter(snapshot -> snapshot.startMillis() >= launchedAt)
            .filter(StartupWait::listenersBound)
            .isPresent();
    }

    private static boolean listenersBound(RuntimeSnapshot snapshot) {
        return !snapshot.gateways().isEmpty()
            && snapshot.gateways().stream().noneMatch(gateway -> gateway.listen().isBlank());
    }

    /**
     * Bounded polling policy.
     *
     * @param attempts    number of readiness observations
     * @param pauseMillis delay between observations
     */
    public record Poll(int attempts, long pauseMillis) {
    }

    /**
     * Identity and polling policy for one launch attempt.
     *
     * @param launchedAt  earliest accepted snapshot timestamp
     * @param expectedPid exact child process expected to become ready
     * @param poll        bounded polling policy
     */
    public record Attempt(long launchedAt, long expectedPid, Poll poll) {
    }
}
