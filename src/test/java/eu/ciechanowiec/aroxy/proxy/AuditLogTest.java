package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit-tests the {@link AuditLog} directly: a sensitive match is masked in {@code audit.log}, a
 * non-sensitive keyword is shown in full, and the redacted-request and raw-response wire dumps land on
 * disk without leaking the secret.
 */
class AuditLogTest {

    private static final String SECRET = "AKIAIOSFODNN7EXAMPLE";
    private static final String ENDPOINT = "/v1/messages";
    private static final String AUDIT = "audit.log";
    private static final int MAX_LINE = 80;
    private static final int TRUNCATED = 77;
    private static final int OVER_LIMIT = MAX_LINE + 1;
    private static final long CAPPED_SENT = 10L;
    private static final long CAPPED_RECEIVED = 20L;

    private static List<ObfuscationMatch> matches() {
        return List.of(
            new ObfuscationMatch("aws_key", "AWS_KEY", SECRET, "<<PII:AWS_KEY:1>>", true),
            new ObfuscationMatch("codename", "CODENAME", "Helios", "<<PII:CODENAME:2>>", false)
        );
    }

    @SneakyThrows
    private static String read(Path file) {
        return Files.readString(file);
    }

    @Test
    @SneakyThrows
    void auditLogMasksSecretShowsKeywordAndDumpsWire(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        String stamp = audit.nextStamp();
        audit.request(stamp, ENDPOINT, matches());
        audit.wireRedactedRequest(
            stamp, "{\"q\":\"see <<PII:AWS_KEY:1>>\"}".getBytes(StandardCharsets.UTF_8), WireBody.TEXT
        );
        audit.wireRawResponse(stamp, "data: <<PII:AWS_KEY:1>>".getBytes(StandardCharsets.UTF_8), WireBody.TEXT);
        String log = read(logs.resolve(AUDIT));
        assertFalse(log.contains(SECRET), "the sensitive original is masked, never written in full");
        assertTrue(log.contains("[masked]"), "the sensitive line is marked masked");
        assertTrue(log.contains("Helios"), "the non-sensitive keyword is shown in full");
        assertWireDumps(logs.resolve("wire"));
    }

    @Test
    @SneakyThrows
    void countsTrafficAsItIsAudited(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        String redacted = audit.nextStamp();
        audit.request(redacted, ENDPOINT, matches());
        audit.wireRedactedRequest(redacted, new byte[] {1, 2, 3}, WireBody.TEXT);
        audit.wireRawResponse(redacted, new byte[] {1, 2, 3, 4, 5}, WireBody.TEXT);
        String clean = audit.nextStamp();
        audit.request(clean, ENDPOINT, List.of());
        TrafficTotals traffic = audit.traffic();
        assertEquals(2L, traffic.requests(), traffic.toString());
        assertEquals(1L, traffic.redacted(), "a request with no match is not redacted: " + traffic);
        assertEquals(2L, traffic.replaced(), traffic.toString());
        assertEquals(3L, traffic.sentBytes(), traffic.toString());
        assertEquals(5L, traffic.receivedBytes(), traffic.toString());
    }

    @Test
    @SneakyThrows
    void aContinuedExchangeIsRedactedOnlyOnce(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        String stamp = audit.nextStamp();
        audit.request(stamp, ENDPOINT, List.of());
        audit.requestContinued(stamp, ENDPOINT, matches(), false);
        audit.requestContinued(stamp, ENDPOINT, matches(), true);
        TrafficTotals traffic = audit.traffic();
        assertEquals(1L, traffic.requests(), traffic.toString());
        assertEquals(1L, traffic.redacted(), traffic.toString());
        assertEquals(4L, traffic.replaced(), traffic.toString());
        assertEquals(3, read(logs.resolve(AUDIT)).split("REQUEST", -1).length - 1, "each frame is audited");
    }

    @Test
    @SneakyThrows
    void countsTheTrueSizeOfACappedDump(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        String stamp = audit.nextStamp();
        audit.wireRedactedRequest(stamp, new byte[1], WireBody.BINARY, CAPPED_SENT);
        audit.wireRawResponse(stamp, new byte[1], WireBody.BINARY, CAPPED_RECEIVED);
        assertEquals(CAPPED_SENT, audit.traffic().sentBytes());
        assertEquals(CAPPED_RECEIVED, audit.traffic().receivedBytes());
    }

    @Test
    @SneakyThrows
    void maskSecretsFalseShowsSensitiveValueInFull(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs, false);
        String stamp = audit.nextStamp();
        audit.request(stamp, ENDPOINT, matches());
        String log = read(logs.resolve(AUDIT));
        assertTrue(log.contains(SECRET), "with mask_secrets=false the sensitive original is shown in full");
        assertFalse(log.contains("[masked]"), "nothing is marked masked when masking is disabled");
    }

    @Test
    @SneakyThrows
    void maskingCanBeChangedForLaterEntries(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs, true);
        audit.request("first", "/responses", matches());
        audit.setMaskSecrets(false);
        audit.request("second", "/responses", matches());
        String log = read(logs.resolve(AUDIT));
        assertFalse(log.substring(0, log.indexOf("[second]")).contains(SECRET), log);
        assertTrue(log.substring(log.indexOf("[second]")).contains(SECRET), log);
    }

    @SneakyThrows
    private static void assertWireDumps(Path wire) {
        try (Stream<Path> entries = Files.list(wire)) {
            List<Path> files = entries.toList();
            assertTrue(
                files.stream().anyMatch(path -> path.getFileName().toString().endsWith("-redacted-request.json")),
                "the redacted request is dumped"
            );
            assertTrue(
                files.stream().anyMatch(path -> path.getFileName().toString().endsWith("-raw-response.txt")),
                "the raw response is dumped"
            );
        }
        assertNoSecretOnDisk(wire);
    }

    @SneakyThrows
    private static void assertNoSecretOnDisk(Path wire) {
        try (Stream<Path> entries = Files.list(wire)) {
            assertTrue(entries.allMatch(AuditLogTest::lacksSecret), "no wire dump contains the secret");
        }
    }

    @SneakyThrows
    private static boolean lacksSecret(Path file) {
        return !read(file).contains(SECRET);
    }

    // The audit line is what a user reads as proof, so its shape is behaviour: the placeholder column
    // lines up, a value that spans lines is collapsed onto one, an over-long one is cut rather than
    // wrapped, and a request that matched nothing says so instead of showing an empty list.
    @Test
    @SneakyThrows
    void rendersOneAlignedLinePerMatch(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        audit.request(
            "stamp", ENDPOINT,
            List.of(
                new ObfuscationMatch("short", "AWS_KEY", "value", "<<PII:AWS_KEY:1>>", false),
                new ObfuscationMatch("wide", "AVERYWIDEPLACEHOLDER", "other", "<<PII:X:2>>", false)
            )
        );
        List<String> lines = read(logs.resolve(AUDIT)).lines().filter(line -> line.contains("->")).toList();
        assertTrue(lines.getFirst().contains("AWS_KEY       value"), lines.getFirst());
        assertTrue(
            lines.get(1).contains("AVERYWIDEPLACEHOLDER other"),
            "a placeholder at or past the column width still gets one separating space: " + lines.get(1)
        );
    }

    @Test
    @SneakyThrows
    void collapsesAndTruncatesAnUnwieldyValue(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        String overLong = "x".repeat(OVER_LIMIT);
        audit.request(
            "stamp", ENDPOINT,
            List.of(
                new ObfuscationMatch("multi", "AWS_KEY", "two\nlines", "<<PII:AWS_KEY:1>>", false),
                new ObfuscationMatch("long", "AWS_KEY", overLong, "<<PII:AWS_KEY:2>>", false)
            )
        );
        String text = read(logs.resolve(AUDIT));
        assertTrue(text.contains("two\\nlines"), "a newline is shown escaped, keeping the entry on one line");
        assertFalse(text.contains(overLong), "an over-long value is cut rather than written in full");
        assertTrue(text.contains("x".repeat(TRUNCATED) + "..."), "the cut is marked");
    }

    // Both limits are inclusive: a value exactly at the line limit is shown whole, and a placeholder
    // exactly at the column width still gets its separating space.
    @Test
    @SneakyThrows
    void treatsBothWidthLimitsAsInclusive(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        String atLimit = "y".repeat(MAX_LINE);
        audit.request(
            "stamp", ENDPOINT,
            List.of(
                new ObfuscationMatch("exact", "AWSKEYPLACEHO", atLimit, "<<PII:AWS_KEY:1>>", false),
                new ObfuscationMatch("wide", "AWSKEYPLACEHOL", "value", "<<PII:AWS_KEY:2>>", false)
            )
        );
        String text = read(logs.resolve(AUDIT));
        assertTrue(text.contains(atLimit), "a value exactly at the limit is not truncated");
        assertTrue(
            text.contains("AWSKEYPLACEHOL value"),
            "a placeholder exactly at the column width keeps one separating space: " + text
        );
    }

    @Test
    @SneakyThrows
    void saysSoWhenNothingMatched(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        audit.request("stamp", ENDPOINT, List.of());
        assertTrue(
            read(logs.resolve(AUDIT)).contains("(no rules matched)"),
            "a request that matched nothing is recorded as such, not left blank"
        );
    }

    @Test
    @SneakyThrows
    void recordsTheResponseWithItsCountAndKind(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        audit.response("stamp", ENDPOINT, 1, true);
        audit.response("stamp", ENDPOINT, 2, false);
        String text = read(logs.resolve(AUDIT));
        assertTrue(text.contains("deobfuscated 1 token (stream)"), text);
        assertTrue(text.contains("deobfuscated 2 tokens (json)"), text);
    }

    // The stamp is what ties an audit line to its wire dumps, so two requests must never share one.
    @Test
    @SneakyThrows
    void issuesADistinctStampPerRequest(@TempDir Path logs) {
        AuditLog audit = new AuditLog(logs);
        String first = audit.nextStamp();
        String second = audit.nextStamp();
        assertFalse(first.isBlank(), "a stamp is never empty, or a dump could not be correlated");
        assertNotEquals(first, second, "two requests get two stamps: " + first + " and " + second);
    }
}
