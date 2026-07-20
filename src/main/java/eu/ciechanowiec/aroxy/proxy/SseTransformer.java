package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.SseDialect;
import eu.ciechanowiec.aroxy.client.SseKind;
import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Deobfuscates a {@code text/event-stream} response on the fly. Incoming bytes are buffered and split
 * into events on the blank-line delimiter, then each block is handled according to how the serving
 * client's {@link SseDialect} classifies it. The transformer itself knows no vendor's event names: it
 * only knows the three {@link SseKind}s.
 *
 * <p>A {@link SseKind#FRAGMENT} has its value fed to a {@link StreamDeobfuscator}, which reverses the
 * part that is now safe and holds back any trailing partial token, because a token can be split across
 * two consecutive fragments. A {@link SseKind#WHOLE} arrives complete and is reversed in one pass. A
 * {@link SseKind#PLAIN} is forwarded unchanged.
 *
 * <p>When a block that ends a run of fragments arrives while a partial is still held, the held text is
 * emitted first by <em>replaying the last fragment block</em> with its value substituted. Replaying
 * rather than authoring a synthetic event is what keeps this class dialect-agnostic: an authored event
 * would have to invent whatever bookkeeping fields the client's parser requires, and a client that
 * rejects an incomplete event would drop the flushed tail entirely.
 *
 * <p>{@link #finish()} flushes any final remainder.
 */
final class SseTransformer {

    private static final String DELIMITER = "\n\n";
    private final OutputStream out;
    private final StreamDeobfuscator deobfuscator;
    private final SseDialect dialect;
    private final ByteArrayOutputStream pending;
    private String lastFragment;

    SseTransformer(OutputStream out, StreamDeobfuscator deobfuscator, SseDialect dialect) {
        this.out = out;
        this.deobfuscator = deobfuscator;
        this.dialect = dialect;
        this.pending = new ByteArrayOutputStream();
        this.lastFragment = "";
    }

    void push(byte[] chunk, int length) throws IOException {
        this.pending.write(chunk, 0, length);
        byte[] buffer = this.pending.toByteArray();
        int start = this.drainEvents(buffer);
        this.out.flush();
        this.pending.reset();
        this.pending.write(buffer, start, buffer.length - start);
    }

    void finish() throws IOException {
        byte[] buffer = this.pending.toByteArray();
        if (buffer.length > 0) {
            this.writeText(this.transform(new String(buffer, StandardCharsets.UTF_8)));
        }
        this.writeText(this.deobfuscator.flush());
        this.out.flush();
        this.pending.reset();
    }

    private int drainEvents(byte[] buffer) throws IOException {
        int start = 0;
        Optional<Delimiter> delimiter = DoubleNewline.find(buffer, start);
        while (delimiter.isPresent()) {
            Delimiter found = delimiter.get();
            this.emitEvent(buffer, start, found.contentEnd());
            start = found.next();
            delimiter = DoubleNewline.find(buffer, start);
        }
        return start;
    }

    private void emitEvent(byte[] buffer, int start, int end) throws IOException {
        String block = new String(buffer, start, end - start, StandardCharsets.UTF_8);
        this.writeText(this.transform(block) + DELIMITER);
    }

    private String transform(String block) {
        return switch (this.dialect.classify(block)) {
            case FRAGMENT -> this.transformFragment(block);
            case WHOLE -> this.flushBefore(this.deobfuscator.restoreWhole(block));
            case PLAIN -> this.maybeFlushBefore(block);
        };
    }

    private String transformFragment(String block) {
        this.lastFragment = block;
        return this.substituted(block, this.deobfuscator.accept(this.valueOf(block)));
    }

    private String maybeFlushBefore(String block) {
        return this.dialect.isTerminal(block) ? this.flushBefore(block) : block;
    }

    private String flushBefore(String block) {
        String held = this.deobfuscator.flush();
        return held.isEmpty() ? block : prepend(this.substituted(this.lastFragment, held), block);
    }

    /**
     * The raw value the given block streams, or the empty string when no rule locates one - which is
     * also the case for the initial {@link #lastFragment}, before any fragment has been seen.
     */
    private String valueOf(String block) {
        return this.locate(block).map(field -> field.value(block)).orElse("");
    }

    /**
     * The given block with the value its rule locates replaced, or the block unchanged when no rule
     * locates one. Replacing nothing is what makes a replay before any fragment harmless.
     */
    private String substituted(String block, String value) {
        return this.locate(block)
            .map(field -> block.substring(0, field.start()) + value + block.substring(field.end()))
            .orElse(block);
    }

    private Optional<SseField> locate(String block) {
        return this.dialect.fragment(block).flatMap(rule -> SseField.locate(block, rule.fieldKey()));
    }

    private static String prepend(CharSequence replayed, String block) {
        return replayed.isEmpty() ? block : replayed + DELIMITER + block;
    }

    private void writeText(String text) throws IOException {
        this.out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
