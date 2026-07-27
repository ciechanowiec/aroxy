package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
