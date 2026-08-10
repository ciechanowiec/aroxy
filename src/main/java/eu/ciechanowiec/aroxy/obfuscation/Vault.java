package eu.ciechanowiec.aroxy.obfuscation;

import eu.ciechanowiec.airness.Justification;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * A stable, bidirectional original&lt;-&gt;token store. The same original always yields the same
 * token, and a token reverses to its exact original. Pairs from non-sensitive rules are persisted to
 * a tab-separated file (the original is {@link Base64}-encoded so values with control characters survive a
 * round trip) so their tokens stay stable across a restart. Pairs from sensitive rules are held in
 * memory only and never reach the file, so no secret is left at rest. The split is automatic and not
 * configurable. If a value first seen through a non-sensitive rule is later matched by a sensitive one
 * (for example after a reload), it is upgraded to sensitive and the persisted line is dropped on the
 * spot. The upgrade is one-way for as long as the pair is resident: no later non-sensitive match
 * writes it back to the file. Eviction drops a pair and its sensitivity together, so a value seen
 * again after being evicted is minted afresh and classified by whichever rule matches it then. That is
 * the treatment the value would get on a first sighting, so it reaches the file only when the
 * configuration itself no longer calls it sensitive. The store holds at most a bounded number of pairs,
 * evicting the least-recently-used ones and rewriting the file when the bound is crossed, so it never
 * grows without limit. Both
 * obfuscation ({@code tokenFor}) and deobfuscation ({@code originalFor}) refresh a pair's recency, so a
 * token still being reversed on responses is never evicted out from under an in-flight reply. All
 * mutating and reading methods are synchronized.
 */
@Slf4j
@Justification(
    "field count is governed by the project's own PMD cap"
)
@SuppressWarnings(
    "ClassWithTooManyFields"
)
public final class Vault {

    private static final char TAB = '\t';
    private static final int MAX_ENTRIES = 50_000;
    private static final int TRIM_TO = 40_000;
    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75F;
    private final Map<String, String> originalToToken;
    private final Map<String, String> tokenToOriginal;
    private final Set<String> sensitiveKeys;
    private final Optional<Path> file;
    private final ReentrantLock lock;
    private final Bounds bounds;
    private int counter;

    /**
     * Creates a vault, optionally backed by a file that persists its non-sensitive entries.
     *
     * @param file the backing file for persisted entries, or empty for an in-memory-only vault
     */
    public Vault(Optional<Path> file) {
        this(file, MAX_ENTRIES, TRIM_TO);
    }

    Vault(Optional<Path> file, int maxEntries, int trimTo) {
        this.originalToToken = new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, true);
        this.tokenToOriginal = new HashMap<>();
        this.sensitiveKeys = new HashSet<>();
        this.lock = new ReentrantLock();
        this.file = file;
        this.bounds = new Bounds(maxEntries, trimTo);
        this.counter = 0;
        file.filter(Files::exists).ifPresent(this::load);
    }

    static Vault inMemory() {
        return new Vault(Optional.empty());
    }

    String tokenFor(String original, String placeholder, boolean sensitive) {
        this.lock.lock();
        try {
            Optional<String> existing = Optional.ofNullable(this.originalToToken.get(original));
            existing.ifPresent(_ -> this.upgradeIfSensitive(original, sensitive));
            return existing.orElseGet(() -> this.mint(original, placeholder, sensitive));
        } finally {
            this.lock.unlock();
        }
    }

    Optional<String> originalFor(String token) {
        this.lock.lock();
        try {
            return Optional.ofNullable(this.tokenToOriginal.get(token)).map(this::keepRecent);
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Refreshes the access-order recency of {@code original} so a token that is still being reversed
     * on responses is not evicted as if it were idle, then returns it unchanged.
     */
    private String keepRecent(String original) {
        this.originalToToken.computeIfPresent(original, (_, token) -> token);
        return original;
    }

    private String mint(String original, String placeholder, boolean sensitive) {
        this.counter++;
        String token = PiiToken.build(placeholder, this.counter);
        this.remember(token, original);
        this.classify(token, original, sensitive);
        this.trimIfNeeded();
        return token;
    }

    private void remember(String token, String original) {
        this.originalToToken.put(original, token);
        this.tokenToOriginal.put(token, original);
    }

    private void classify(String token, String original, boolean sensitive) {
        if (sensitive) {
            this.sensitiveKeys.add(original);
        } else {
            this.append(token, original);
        }
    }

    private void upgradeIfSensitive(String original, boolean sensitive) {
        if (sensitive && this.sensitiveKeys.add(original)) {
            this.rewrite();
        }
    }

    private void trimIfNeeded() {
        if (this.originalToToken.size() > this.bounds.max()) {
            this.trim();
        }
    }

    private void trim() {
        Iterator<Map.Entry<String, String>> entries = this.originalToToken.entrySet().iterator();
        int trimTarget = this.bounds.trim();
        while (this.originalToToken.size() > trimTarget) {
            this.evictOne(entries);
        }
        this.rewrite();
    }

    private void evictOne(Iterator<Map.Entry<String, String>> entries) {
        Map.Entry<String, String> eldest = entries.next();
        this.tokenToOriginal.remove(eldest.getValue());
        this.sensitiveKeys.remove(eldest.getKey());
        entries.remove();
    }

    private void load(Path source) {
        try {
            Files.readAllLines(source).forEach(this::loadLine);
            this.trimIfNeeded();
        } catch (IOException exception) {
            log.warn("Could not read vault {}: {}", source, exception.getMessage());
        }
    }

    private void loadLine(String line) {
        int tab = line.indexOf(TAB);
        if (tab < 0) {
            return;
        }
        String token = line.substring(0, tab);
        try {
            this.remember(token, decode(line.substring(tab + 1)));
            this.counter = Math.max(this.counter, PiiToken.indexOf(token));
        } catch (IllegalArgumentException exception) {
            log.warn("Skipping malformed vault line for token {}: {}", token, exception.getMessage());
        }
    }

    private void append(String token, String original) {
        this.file.ifPresent(target -> this.writeLine(target, token, original));
    }

    private void writeLine(Path target, String token, String original) {
        String line = token + TAB + encode(original) + System.lineSeparator();
        try {
            Files.writeString(
                target, line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            log.warn("Could not persist vault {}: {}", target, exception.getMessage());
        }
    }

    private void rewrite() {
        this.file.ifPresent(this::rewriteTo);
    }

    private void rewriteTo(Path target) {
        Path temporary = target.resolveSibling(target.getFileName() + ".compact");
        try {
            Files.write(
                temporary, this.snapshotLines(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            );
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            log.warn("Could not compact vault {}: {}", target, exception.getMessage());
        }
    }

    private List<String> snapshotLines() {
        return this.originalToToken.entrySet().stream()
            .filter(entry -> !this.sensitiveKeys.contains(entry.getKey()))
            .map(entry -> entry.getValue() + TAB + encode(entry.getKey()))
            .toList();
    }

    private static String encode(String original) {
        return Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private record Bounds(int max, int trim) {
    }
}
