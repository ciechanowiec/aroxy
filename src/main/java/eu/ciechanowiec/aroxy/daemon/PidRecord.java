package eu.ciechanowiec.aroxy.daemon;

import java.time.Instant;
import java.util.Optional;

/**
 * The contents of the pid file: the daemon's process id and, when the operating system exposed it, the
 * process start time. The start time is what distinguishes the real daemon from an unrelated process
 * the OS later assigned the same recycled pid, so {@link #matches(ProcessHandle)} treats a live pid
 * whose start time differs as not the daemon. A record without a recorded start time (a hand-written
 * pid file) falls back to a plain liveness check.
 */
record PidRecord(long pid, Optional<Instant> start) {

    private static final String SEPARATOR = "\t";

    static Optional<PidRecord> parse(String text) {
        String[] parts = text.split(SEPARATOR, 2);
        Optional<Instant> start = parts.length < 2 ? Optional.empty() : instantOf(parts[1].strip());
        return pidOf(parts[0]).map(value -> new PidRecord(value, start));
    }

    String serialized() {
        return this.start.map(instant -> this.pid + SEPARATOR + instant.toEpochMilli())
            .orElseGet(() -> Long.toString(this.pid));
    }

    boolean matches(ProcessHandle handle) {
        return this.start.map(instant -> sameStart(handle, instant)).orElseGet(handle::isAlive);
    }

    private static boolean sameStart(ProcessHandle handle, Instant stored) {
        return handle.info().startInstant()
            .map(actual -> actual.toEpochMilli() == stored.toEpochMilli()).orElse(false);
    }

    private static Optional<Long> pidOf(String text) {
        try {
            return Optional.of(Long.parseLong(text.strip()));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> instantOf(String text) {
        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(text)));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }
}
