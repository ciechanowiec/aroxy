package eu.ciechanowiec.aroxy.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The streaming deobfuscator reverses tokens across fragment boundaries: a token split between two fragments still
 * reverses, the reversed count is tracked, and a trailing partial marker is held back until completed or flushed.
 */
class StreamDeobfuscatorTest {

    private static final String PLACEHOLDER = "AWS_KEY";
    private static final String ORIGINAL = "AKIAIOSFODNN7EXAMPLE";
    private static final int LONG_RUN = 70;

    private static StreamDeobfuscator stream(Vault vault) {
        return new StreamDeobfuscator(new Obfuscator(List.of(), vault));
    }

    @Test
    void restoreWholeReversesACompleteBlockInOnePass() {
        Vault vault = Vault.inMemory();
        String token = vault.tokenFor(ORIGINAL, PLACEHOLDER, false);
        assertEquals(
            "whole " + ORIGINAL + " block", stream(vault).restoreWhole("whole " + token + " block"),
            "a complete block is reversed on its own, since no token in it can be split"
        );
    }

    @Test
    void restoreWholeLeavesTheHeldPartialForTheFragmentRunToFinish() {
        Vault vault = Vault.inMemory();
        String token = vault.tokenFor(ORIGINAL, PLACEHOLDER, false);
        int half = token.length() / 2;
        StreamDeobfuscator stream = stream(vault);
        stream.accept("start " + token.substring(0, half));
        stream.restoreWhole("an unrelated whole block");
        assertEquals(
            ORIGINAL + " end", stream.accept(token.substring(half) + " end"),
            "the carry survived the whole-block pass, so the split token still completes"
        );
    }

    @Test
    void restoreWholeCountsWhatItReverses() {
        Vault vault = Vault.inMemory();
        String token = vault.tokenFor(ORIGINAL, PLACEHOLDER, false);
        StreamDeobfuscator stream = stream(vault);
        assertEquals(0, stream.reversedCount(), "nothing reversed yet");
        stream.restoreWhole("a block naming " + token + " once");
        assertEquals(1, stream.reversedCount(), "a whole-block reversal is audited like any other");
    }

    @Test
    void reversesTokenSplitAcrossTwoFragments() {
        Vault vault = Vault.inMemory();
        String token = vault.tokenFor(ORIGINAL, PLACEHOLDER, false);
        int half = token.length() / 2;
        StreamDeobfuscator stream = stream(vault);
        String first = stream.accept("start " + token.substring(0, half));
        String second = stream.accept(token.substring(half) + " end");
        String tail = stream.flush();
        assertEquals("start " + ORIGINAL + " end", first + second + tail, "split token reverses");
        assertEquals(1, stream.reversedCount(), "one token reversed");
    }

    @Test
    void holdsBackPartialMarkerUntilFlush() {
        StreamDeobfuscator stream = stream(Vault.inMemory());
        String emitted = stream.accept("tail <<PII");
        assertEquals("tail ", emitted, "partial marker held back");
        assertEquals("<<PII", stream.flush(), "remainder flushed unchanged when never completed");
        assertEquals(0, stream.reversedCount(), "no complete token seen");
    }

    @Test
    void emitsPlainTextImmediately() {
        StreamDeobfuscator stream = stream(Vault.inMemory());
        assertEquals("hello world", stream.accept("hello world"), "plain text passes straight through");
    }

    @Test
    void doesNotHoldBackVeryLongRunAfterMarker() {
        StreamDeobfuscator stream = stream(Vault.inMemory());
        String tail = "x".repeat(LONG_RUN);
        String emitted = stream.accept("<<PII:" + tail);
        assertTrue(emitted.contains(tail), "a run longer than the cap is treated as ordinary text");
    }
}
