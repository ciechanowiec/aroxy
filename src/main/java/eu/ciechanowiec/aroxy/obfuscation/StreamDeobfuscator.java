package eu.ciechanowiec.aroxy.obfuscation;

/**
 * Reverses tokens across a sequence of logical text fragments (for example the {@code delta.text}
 * values of a streamed response). Because a token the model echoes can be split across two
 * fragments, this class holds back any trailing partial token until a later fragment completes it,
 * and {@link #flush()} emits whatever remains. It also tracks how many tokens it has reversed.
 *
 * <p>This operates on already-extracted text; parsing the surrounding SSE event framing is the
 * concern of a separate, later layer.
 */
public final class StreamDeobfuscator {

    private final Obfuscator obfuscator;
    private String carry;
    private int reversed;

    /**
     * Creates a streaming deobfuscator that reverses tokens using the given obfuscator's vault.
     *
     * @param obfuscator the obfuscator whose vault backs token restoration
     */
    public StreamDeobfuscator(Obfuscator obfuscator) {
        this.obfuscator = obfuscator;
        this.carry = "";
        this.reversed = 0;
    }

    /**
     * Accepts the next fragment and returns the portion that is now safe to emit, reversed.
     */
    public String accept(String fragment) {
        String pending = this.carry + fragment;
        int boundary = TokenBoundary.of(pending);
        this.carry = pending.substring(boundary);
        return this.reverse(pending.substring(0, boundary));
    }

    /**
     * Emits and reverses any held-back remainder; call once the stream is complete.
     */
    public String flush() {
        String remainder = this.carry;
        this.carry = "";
        return this.reverse(remainder);
    }

    /**
     * The total number of tokens reversed across every fragment accepted so far.
     *
     * @return the running count of reversed tokens
     */
    public int reversedCount() {
        return this.reversed;
    }

    private String reverse(String text) {
        Reversal reversal = this.obfuscator.restore(text);
        this.reversed += reversal.count();
        return reversal.text();
    }
}
