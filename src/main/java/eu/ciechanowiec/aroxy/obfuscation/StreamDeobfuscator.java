package eu.ciechanowiec.aroxy.obfuscation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reverses tokens across a sequence of logical text fragments (for example the {@code delta.text}
 * values of a streamed response). Because a token the model echoes can be split across two
 * fragments, this class holds back any trailing partial token until a later fragment completes it,
 * and {@link #flush()} emits whatever remains. It also tracks how many tokens it has reversed.
 *
 * <p>This operates on already-extracted text. Parsing the surrounding SSE event framing is the
 * concern of a separate, later layer.
 */
public final class StreamDeobfuscator {

    private final Obfuscator obfuscator;

    /**
     * The trailing partial token held back for a later fragment, in a reference rather than a
     * reassigned field. What moves between fragments is the text it holds, not the buffer itself.
     */
    private final AtomicReference<String> carry;

    private final AtomicInteger reversed;

    /**
     * Creates a streaming deobfuscator that reverses tokens using the given obfuscator's vault.
     *
     * @param obfuscator the obfuscator whose vault backs token restoration
     */
    public StreamDeobfuscator(Obfuscator obfuscator) {
        this.obfuscator = obfuscator;
        this.carry = new AtomicReference<>("");
        this.reversed = new AtomicInteger();
    }

    /**
     * Accepts the next fragment and returns the portion that is now safe to emit, reversed.
     *
     * @param fragment the next logical text fragment
     * @return the reversed text that is safe to emit
     */
    public String accept(String fragment) {
        String pending = this.held() + fragment;
        int boundary = TokenBoundary.of(pending);
        this.carry.set(pending.substring(boundary));
        return this.reverse(pending.substring(0, boundary));
    }

    /**
     * Emits and reverses any held-back remainder. Call once the stream is complete.
     *
     * @return the reversed remainder
     */
    public String flush() {
        String remainder = this.carry.getAndSet("");
        return this.reverse(remainder);
    }

    /**
     * Reverses a complete piece of text in one pass, without touching the carry buffer.
     *
     * <p>Some streams repeat the text they streamed as fragments in a single later block that carries
     * the authoritative copy. Such a block arrives whole, so no token in it can be split and the carry
     * buffer must be left alone for the fragment run it belongs to. Reversing that copy matters
     * because a client may persist it into its conversation history and echo it back on the next
     * request. Reversing only the fragments would leave tokens in that history to egress again.
     *
     * @param text the complete text to reverse
     * @return the text with every token restored to its original value
     */
    public String restoreWhole(String text) {
        return this.reverse(text);
    }

    /**
     * Whether a trailing partial token is currently held back, waiting for a later fragment.
     *
     * @return whether the carry buffer is non-empty
     */
    public boolean holding() {
        return !this.held().isEmpty();
    }

    /**
     * The total number of tokens reversed across every fragment accepted so far.
     *
     * @return the running count of reversed tokens
     */
    public int reversedCount() {
        return this.reversed.get();
    }

    private String held() {
        return Objects.requireNonNullElse(this.carry.get(), "");
    }

    private String reverse(String text) {
        Reversal reversal = this.obfuscator.restore(text);
        this.reversed.addAndGet(reversal.count());
        return reversal.text();
    }
}
