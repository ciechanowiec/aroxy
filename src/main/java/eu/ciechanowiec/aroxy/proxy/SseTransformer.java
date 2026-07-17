package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Deobfuscates a {@code text/event-stream} response on the fly. Incoming bytes are buffered and split
 * into events on the blank-line delimiter ({@code \n\n}). For a {@code content_block_delta} carrying a
 * {@code text_delta} or {@code input_json_delta}, the raw (still JSON-escaped) value is fed to a
 * {@link StreamDeobfuscator}, which reverses the part that is now safe and holds back any trailing
 * partial token; the returned text is substituted back into the field while the framing is preserved.
 *
 * <p>Because the model can split one token across two delta events, the carry buffer bridges them.
 * When a terminal event ({@code content_block_stop}/{@code message_stop}) arrives and a partial is
 * still held, it is flushed as a synthetic {@code text_delta} event (with the last seen index) before
 * that event is forwarded. {@link #finish()} flushes any final remainder.
 */
final class SseTransformer {

    private final OutputStream out;
    private final StreamDeobfuscator deobfuscator;
    private final ByteArrayOutputStream pending;
    private int lastIndex;
    private DeltaKind lastDelta;

    SseTransformer(OutputStream out, StreamDeobfuscator deobfuscator) {
        this.out = out;
        this.deobfuscator = deobfuscator;
        this.pending = new ByteArrayOutputStream();
        this.lastIndex = 0;
        this.lastDelta = DeltaKind.TEXT;
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
        this.writeText(this.transform(block) + "\n\n");
    }

    private String transform(String block) {
        SseEvent event = new SseEvent(block);
        event.index().ifPresent(this::rememberIndex);
        if (event.isTextDelta()) {
            return this.transformDelta(block);
        }
        return this.maybeFlushBefore(event, block);
    }

    private void rememberIndex(int index) {
        this.lastIndex = index;
    }

    private String transformDelta(String block) {
        this.lastDelta = DeltaKind.of(block);
        return SseField.locate(block).map(field -> this.substitute(block, field)).orElse(block);
    }

    private String substitute(String block, SseField field) {
        String reversed = this.deobfuscator.accept(field.value(block));
        return block.substring(0, field.start()) + reversed + block.substring(field.end());
    }

    private String maybeFlushBefore(SseEvent event, String block) {
        if (!event.isTerminal()) {
            return block;
        }
        return this.prependFlush(block);
    }

    private String prependFlush(String block) {
        String flushed = this.deobfuscator.flush();
        return flushed.isEmpty() ? block : this.synthetic(flushed) + "\n\n" + block;
    }

    private String synthetic(String text) {
        return "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":" + this.lastIndex
            + ",\"delta\":{\"type\":\"" + this.lastDelta.type() + "\",\""
            + this.lastDelta.field() + "\":\"" + text + "\"}}";
    }

    private void writeText(String text) throws IOException {
        this.out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
