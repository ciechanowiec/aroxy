package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.fs.RestrictedDir;
import eu.ciechanowiec.aroxy.obfuscation.MaskedSecret;
import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes proof artifacts directly to disk (not via the logging framework, so it is unit-testable): a
 * human-readable before/after audit to {@code audit.log} with size-based rotation, and wire dumps of
 * the redacted request and the raw response under {@code logs/wire/}. A sensitive match's original is
 * masked via {@link MaskedSecret}. A non-sensitive match (for example a keyword) is shown in full.
 * No secret and no pre-obfuscation original is ever written.
 *
 * <p>Because every agent's exchange passes through here, this is also where the {@link TrafficTotals}
 * that {@code aroxy status} reports are counted.
 */
public final class AuditLog {

    private static final DateTimeFormatter LINE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter STAMP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final int PLACEHOLDER_WIDTH = 14;
    private static final int MAX_LINE = 80;
    private static final int TRUNCATED = 77;
    private final RollingFile audit;
    private final WireDumps wire;
    private final AtomicInteger sequence;
    private final AtomicBoolean maskSecrets;
    private final TrafficStats traffic;

    /**
     * Creates an audit log under {@code logsDirectory} with sensitive originals masked.
     *
     * @param logsDirectory the directory the audit log and wire dumps are written under
     * @throws IOException if the log directory cannot be created
     */
    public AuditLog(Path logsDirectory) throws IOException {
        this(logsDirectory, true);
    }

    /**
     * Creates an audit log under {@code logsDirectory}, choosing whether sensitive originals are masked.
     *
     * @param logsDirectory the directory the audit log and wire dumps are written under
     * @param maskSecrets   whether sensitive-rule originals are masked in the audit log
     * @throws IOException if the log directory cannot be created
     */
    public AuditLog(Path logsDirectory, boolean maskSecrets) throws IOException {
        RestrictedDir.create(logsDirectory);
        this.audit = new RollingFile(logsDirectory.resolve("audit.log"));
        this.wire = new WireDumps(logsDirectory.resolve("wire"));
        this.sequence = new AtomicInteger();
        this.maskSecrets = new AtomicBoolean(maskSecrets);
        this.traffic = new TrafficStats();
    }

    private static void appendMatches(
        StringBuilder builder, Collection<ObfuscationMatch> matches, boolean maskSecrets
    ) {
        if (matches.isEmpty()) {
            builder.append("    (no rules matched)\n");
        }
        matches.forEach(match -> appendMatch(builder, match, maskSecrets));
    }

    private static void appendMatch(StringBuilder builder, ObfuscationMatch match, boolean maskSecrets) {
        boolean masked = match.sensitive() && maskSecrets;
        String shown = masked ? MaskedSecret.mask(match.original()) : oneLine(match.original());
        String tag = masked ? "  [masked]" : "";
        builder.append("    ").append(pad(match.placeholder())).append(shown)
            .append("  ->  ").append(match.token()).append(tag).append('\n');
    }

    private static String oneLine(String value) {
        String collapsed = value.replace("\n", "\\n").replace("\r", "");
        boolean tooLong = collapsed.length() > MAX_LINE;
        return tooLong ? collapsed.substring(0, TRUNCATED) + "..." : collapsed;
    }

    private static String pad(CharSequence text) {
        boolean wide = text.length() >= PLACEHOLDER_WIDTH;
        return text + (wide ? " " : " ".repeat(PLACEHOLDER_WIDTH - text.length()));
    }

    /**
     * Returns a per-request stamp that correlates an audit line with its wire dumps. Each stamp opens
     * one in-scope exchange, so this is also where the exchange is counted.
     */
    String nextStamp() {
        this.traffic.exchanged();
        int next = this.sequence.incrementAndGet();
        return LocalDateTime.now(ZoneId.systemDefault()).format(STAMP_TS) + "-r" + String.format("%04d", next);
    }

    /**
     * Records the matches that fired for one request, masking sensitive originals.
     *
     * @param stamp    the per-request stamp correlating this line with its wire dumps
     * @param endpoint the request endpoint
     * @param matches  the matches that fired for this request
     */
    public void request(String stamp, String endpoint, Collection<ObfuscationMatch> matches) {
        this.traffic.matched(matches.size(), !matches.isEmpty());
        this.appendRequest(stamp, endpoint, matches);
    }

    /**
     * Records the matches that fired in a later frame of an exchange already recorded by
     * {@link #request(String, String, Collection)}, as a streamed Cursor upload has. The audit line is
     * the same, but the exchange counts as redacted only once, however many of its frames matched.
     *
     * @param stamp           the stamp of the exchange the frame belongs to
     * @param endpoint        the request endpoint
     * @param matches         the matches that fired in this frame
     * @param alreadyRedacted whether an earlier frame of the same exchange already matched
     */
    void requestContinued(
        String stamp, String endpoint, Collection<ObfuscationMatch> matches, boolean alreadyRedacted
    ) {
        this.traffic.matched(matches.size(), !alreadyRedacted && !matches.isEmpty());
        this.appendRequest(stamp, endpoint, matches);
    }

    private void appendRequest(String stamp, String endpoint, Collection<ObfuscationMatch> matches) {
        StringBuilder builder = new StringBuilder();
        builder.append(LINE_TS.format(LocalDateTime.now(ZoneId.systemDefault())))
            .append("  REQUEST  ").append(endpoint).append("  [").append(stamp).append("]\n");
        appendMatches(builder, matches, this.maskSecrets.get());
        this.audit.append(builder.toString());
    }

    /**
     * Changes how later audit entries display sensitive matches. Existing entries remain exactly as
     * they were written.
     *
     * @param enabled whether sensitive values should be masked
     */
    public void setMaskSecrets(boolean enabled) {
        this.maskSecrets.set(enabled);
    }

    /**
     * Records that a response was deobfuscated, with how many tokens were reversed.
     *
     * @param stamp      the per-request stamp correlating this line with its request
     * @param endpoint   the request endpoint
     * @param tokenCount the number of tokens reversed in the response
     * @param streamed   whether the response was a stream rather than a single JSON body
     */
    public void response(String stamp, String endpoint, int tokenCount, boolean streamed) {
        String kind = streamed ? "stream" : "json";
        String plural = tokenCount == 1 ? "" : "s";
        this.audit.append(
            LINE_TS.format(LocalDateTime.now(ZoneId.systemDefault())) + "  RESPONSE " + endpoint + "  ["
                + stamp + "]    deobfuscated " + tokenCount + " token" + plural + " (" + kind + ")\n"
        );
    }

    /**
     * The traffic carried since this audit log was created, summed over every listener sharing it.
     *
     * @return a point-in-time copy of the traffic counters
     */
    public TrafficTotals traffic() {
        return this.traffic.totals();
    }

    void wireRedactedRequest(String stamp, byte[] body, WireBody kind) {
        this.wireRedactedRequest(stamp, body, kind, body.length);
    }

    /**
     * Dumps the redacted request and counts {@code wireBytes} as sent. The two differ only for a
     * stream whose dump was capped, where the bytes actually sent exceed the bytes kept.
     */
    void wireRedactedRequest(String stamp, byte[] body, WireBody kind, long wireBytes) {
        this.traffic.sent(wireBytes);
        this.wire.redactedRequest(stamp, body, kind);
    }

    void wireRawResponse(String stamp, byte[] body, WireBody kind) {
        this.wireRawResponse(stamp, body, kind, body.length);
    }

    /**
     * Dumps the raw response and counts {@code wireBytes} as received. The two differ only for a
     * stream whose dump was capped, where the bytes actually received exceed the bytes kept.
     */
    void wireRawResponse(String stamp, byte[] body, WireBody kind, long wireBytes) {
        this.traffic.received(wireBytes);
        this.wire.rawResponse(stamp, body, kind);
    }
}
