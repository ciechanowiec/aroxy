package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.governance.Justification;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The vault is stable and bidirectional. Pairs from non-sensitive rules persist to a file that a fresh vault reloads
 * (so a token reverses across restarts and the counter does not collide), while pairs from sensitive rules stay in
 * memory and never reach the file.
 */
class VaultTest {

    private static final String PLACEHOLDER = "AWS_KEY";
    private static final String ORIGINAL = "AKIAIOSFODNN7EXAMPLE";
    private static final String COMPANY = "COMPANY";
    private static final String VAULT_FILE = "vault.tsv";
    private static final boolean SENSITIVE = true;
    private static final boolean DURABLE = false;
    private static final int BOUND_MAX = 3;
    private static final int BOUND_TRIM = 2;
    private static final int PERSISTED_COUNT = 5;

    @Test
    void returnsSameTokenForSameOriginal() {
        Vault vault = Vault.inMemory();
        String first = vault.tokenFor(ORIGINAL, PLACEHOLDER, DURABLE);
        String second = vault.tokenFor(ORIGINAL, PLACEHOLDER, DURABLE);
        assertEquals(first, second, "stable token");
        assertEquals(Optional.of(ORIGINAL), vault.originalFor(first), "reverses");
    }

    @Test
    void incrementsCounterForDistinctOriginals() {
        Vault vault = Vault.inMemory();
        String first = vault.tokenFor("one", PLACEHOLDER, DURABLE);
        String second = vault.tokenFor("two", PLACEHOLDER, DURABLE);
        assertNotEquals(first, second, "distinct originals get distinct tokens");
    }

    @Test
    void returnsEmptyForUnknownToken() {
        assertTrue(Vault.inMemory().originalFor("<<PII:NOPE:99>>").isEmpty(), "unknown token");
    }

    @Test
    void persistsAndReloadsAcrossInstances(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        String token = new Vault(Optional.of(file)).tokenFor(ORIGINAL, PLACEHOLDER, DURABLE);
        Vault reloaded = new Vault(Optional.of(file));
        assertEquals(Optional.of(ORIGINAL), reloaded.originalFor(token), "reloaded from disk");
        assertEquals(token, reloaded.tokenFor(ORIGINAL, PLACEHOLDER, DURABLE), "no duplicate after reload");
        assertNotEquals(reloaded.tokenFor("new", PLACEHOLDER, DURABLE), token, "counter continues past reload");
    }

    @Test
    @SneakyThrows
    void keepsSensitivePairsInMemoryAndOffDisk(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        Vault vault = new Vault(Optional.of(file));
        String identifier = vault.tokenFor("Acme Corporation", "COMPANY", DURABLE);
        String secret = vault.tokenFor(ORIGINAL, PLACEHOLDER, SENSITIVE);
        assertEquals(Optional.of(ORIGINAL), vault.originalFor(secret), "secret reverses while in memory");
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(identifier)), "identifier is persisted");
        assertFalse(lines.stream().anyMatch(line -> line.startsWith(secret)), "secret never written to disk");
        Vault reloaded = new Vault(Optional.of(file));
        assertTrue(reloaded.originalFor(secret).isEmpty(), "secret does not survive a restart");
        assertEquals(Optional.of("Acme Corporation"), reloaded.originalFor(identifier), "identifier survives");
    }

    @Test
    @SneakyThrows
    void upgradesAReclassifiedValueToSensitiveAndPurgesItFromDisk(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        Vault vault = new Vault(Optional.of(file));
        String token = vault.tokenFor("Acme Corporation", "COMPANY", DURABLE);
        assertTrue(
            Files.readAllLines(file).stream().anyMatch(line -> line.startsWith(token)),
            "non-sensitive value starts on disk"
        );
        String again = vault.tokenFor("Acme Corporation", "COMPANY", SENSITIVE);
        assertEquals(token, again, "the token stays stable across reclassification");
        assertEquals(Optional.of("Acme Corporation"), vault.originalFor(token), "still reverses in memory");
        assertFalse(
            Files.readAllLines(file).stream().anyMatch(line -> line.startsWith(token)),
            "the value is purged from disk once it is matched by a sensitive rule"
        );
    }

    @Test
    @SneakyThrows
    void skipsAMalformedLineButStillLoadsTheValidOnes(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        String token = new Vault(Optional.of(file)).tokenFor(ORIGINAL, PLACEHOLDER, DURABLE);
        String good = Files.readAllLines(file).getFirst();
        Files.write(file, List.of("<<PII:BAD:7>>\tnot_valid_base64!!!", good));
        Vault reloaded = new Vault(Optional.of(file));
        assertEquals(
            Optional.of(ORIGINAL), reloaded.originalFor(token),
            "a corrupt line is skipped without aborting the rest of the load"
        );
    }

    @Justification(
        "\"v\" + index builds sequential fixture names like v1, v2. No whitespace is intended"
    )
    @SuppressWarnings(
        "StringConcatenationMissingWhitespace"
    )
    @Test
    @SneakyThrows
    void boundsTheStoreAndCompactsTheFile(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        Vault vault = new Vault(Optional.of(file), 4, 2);
        String oldest = vault.tokenFor("v0", PLACEHOLDER, DURABLE);
        String newest = "";
        for (int index = 1; index <= 4; index++) {
            newest = vault.tokenFor("v" + index, PLACEHOLDER, DURABLE);
        }
        assertTrue(vault.originalFor(oldest).isEmpty(), "least-recently-used pair evicted");
        assertEquals(Optional.of("v4"), vault.originalFor(newest), "recent pair kept");
        assertTrue(Files.readAllLines(file).size() <= 4, "file compacted below the mint count");
    }

    @Test
    void doesNotTrimWhileExactlyAtTheMaximum() {
        Vault vault = new Vault(Optional.empty(), BOUND_MAX, BOUND_TRIM);
        String first = vault.tokenFor("v1", PLACEHOLDER, DURABLE);
        vault.tokenFor("v2", PLACEHOLDER, DURABLE);
        vault.tokenFor("v3", PLACEHOLDER, DURABLE);
        assertEquals(Optional.of("v1"), vault.originalFor(first), "a store exactly at the maximum is not trimmed");
    }

    @Test
    void trimStopsAtTheFloorAndKeepsTheFloorManyNewest() {
        Vault vault = new Vault(Optional.empty(), BOUND_MAX, BOUND_TRIM);
        vault.tokenFor("v1", PLACEHOLDER, DURABLE);
        vault.tokenFor("v2", PLACEHOLDER, DURABLE);
        String third = vault.tokenFor("v3", PLACEHOLDER, DURABLE);
        String fourth = vault.tokenFor("v4", PLACEHOLDER, DURABLE);
        assertEquals(Optional.of("v3"), vault.originalFor(third), "trimming halts at the floor, not one below it");
        assertEquals(Optional.of("v4"), vault.originalFor(fourth), "the newest pair survives the trim");
    }

    @Justification(
        "\"v\" + index builds sequential fixture names like v1, v2. No whitespace is intended"
    )
    @SuppressWarnings(
        "StringConcatenationMissingWhitespace"
    )
    @Test
    void trimsAnOverFullFileWhileLoadingIt(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        Vault writer = new Vault(Optional.of(file));
        List<String> tokens = new ArrayList<>();
        for (int index = 1; index <= PERSISTED_COUNT; index++) {
            tokens.add(writer.tokenFor("v" + index, PLACEHOLDER, DURABLE));
        }
        Vault reloaded = new Vault(Optional.of(file), BOUND_MAX, BOUND_TRIM);
        assertTrue(reloaded.originalFor(tokens.getFirst()).isEmpty(), "an over-full file is trimmed down as it loads");
        assertEquals(Optional.of("v5"), reloaded.originalFor(tokens.getLast()), "the newest loaded pair survives");
    }

    @Test
    @SneakyThrows
    void loadsALineWhoseTokenIsEmpty(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        Files.write(file, List.of("\taGVsbG8="));
        Vault reloaded = new Vault(Optional.of(file));
        assertEquals(
            Optional.of("hello"), reloaded.originalFor(""),
            "a tab at index zero yields an empty token, and the line is still loaded rather than skipped"
        );
    }

    @Test
    @SneakyThrows
    void evictionDropsAPairsSensitivityAlongWithIt(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        Vault vault = new Vault(Optional.of(file), 2, 1);
        vault.tokenFor("UpCo", COMPANY, SENSITIVE);
        vault.tokenFor("KeepCo", COMPANY, DURABLE);
        vault.tokenFor("OtherCo", COMPANY, DURABLE);
        String reminted = vault.tokenFor("UpCo", COMPANY, DURABLE);
        assertTrue(
            Files.readAllLines(file).stream().anyMatch(line -> line.startsWith(reminted)),
            "an evicted pair is classified afresh, so a later non-sensitive match persists it: the one-way "
                + "upgrade holds only while the pair is resident"
        );
    }

    @Test
    @SneakyThrows
    void rewritePreservesTheOtherNonSensitiveLines(
        @TempDir Path directory
    ) {
        Path file = directory.resolve(VAULT_FILE);
        Vault vault = new Vault(Optional.of(file));
        String kept = vault.tokenFor("KeepCo", COMPANY, DURABLE);
        vault.tokenFor("UpCo", COMPANY, DURABLE);
        vault.tokenFor("UpCo", COMPANY, SENSITIVE);
        List<String> lines = Files.readAllLines(file);
        assertTrue(
            lines.stream().anyMatch(line -> line.startsWith(kept)),
            "compacting after an upgrade rewrites the surviving non-sensitive pairs, not an empty file"
        );
    }
}
