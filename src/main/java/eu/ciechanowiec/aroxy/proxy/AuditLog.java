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
 * masked via {@link MaskedSecret}; a non-sensitive match (for example a keyword) is shown in full.
 * No secret and no pre-obfuscation original is ever written.
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
     * Returns a per-request stamp that correlates an audit line with its wire dumps.
     */
    String nextStamp() {
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

    void wireRedactedRequest(String stamp, byte[] body) {
        this.wire.redactedRequest(stamp, body);
    }

    void wireRawResponse(String stamp, byte[] body) {
        this.wire.rawResponse(stamp, body);
    }
}
