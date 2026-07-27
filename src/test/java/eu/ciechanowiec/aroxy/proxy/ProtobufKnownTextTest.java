package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Wire type 2 is ambiguous, so the transformer tries both readings of a value and keeps the one that
 * matched. A reading it discards still ran, which is harmless for a pure transform and not for one
 * holding a carry buffer between calls, as Cursor's streamed delta does. A leaf therefore declares the
 * paths it knows to be text, and the transformer stops guessing at those: it calls the leaf once, with
 * the whole value, and never recurses past it.
 */
class ProtobufKnownTextTest {

    private static final List<Integer> DECLARED = List.of(1);
    private static final List<Integer> NESTED = List.of(1, 4);
    private static final int PRINTABLE_TAG = 0x22;
    private static final int PRINTABLE_PADDING = 22;
    private static final String TEXT = "ExampleCorp";

    @Test
    void callsADeclaredTextLeafOnceAndDoesNotRecurseIntoIt() {
        Recorder leaf = new Recorder(DECLARED);
        byte[] message = field(printableMessage(TEXT + "x".repeat(PRINTABLE_PADDING)));
        transformer().inbound(message, leaf);
        assertEquals(List.of(DECLARED), leaf.paths(), "the declared path is the only leaf reached");
    }

    // The same bytes without the declaration: they parse as a well-formed message, so the transformer
    // recurses and reaches the inner leaf instead. That is the reading the declaration suppresses, and
    // asserting it here is what makes the test above about the declaration rather than about the bytes.
    @Test
    void recursesIntoTheSameValueWhenNothingIsDeclared() {
        Recorder leaf = new Recorder(List.of());
        byte[] message = field(printableMessage(TEXT + "x".repeat(PRINTABLE_PADDING)));
        transformer().inbound(message, leaf);
        assertTrue(leaf.paths().contains(NESTED), "the nested reading reaches the inner leaf");
    }

    @Test
    void handsADeclaredLeafTheWholeValueRatherThanItsInnerField() {
        Recorder leaf = new Recorder(DECLARED);
        byte[] value = printableMessage(TEXT + "x".repeat(PRINTABLE_PADDING));
        transformer().inbound(field(value), leaf);
        assertArrayEquals(value, leaf.contents().getFirst(), "the leaf sees the value as one text");
    }

    // A declared path is exempt from the guessing, not from the transformation: what the leaf returns is
    // what the rewritten message carries, with its length prefix corrected to the new size.
    @Test
    void writesWhatTheDeclaredLeafReturned() {
        byte[] restored = transformer().inbound(field(TEXT), new Recorder(DECLARED)).payload();
        assertArrayEquals(field(TEXT + Recorder.MARK), restored);
    }

    private static ProtobufTransformer transformer() {
        return new ProtobufTransformer(
            new ProtobufText(new Obfuscator(List.of(), new Vault(Optional.empty())))
        );
    }

    private static byte[] field(String text) {
        return field(text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] field(byte[] value) {
        ByteArrayOutputStream field = new ByteArrayOutputStream(value.length + 2);
        field.write(10);
        field.write(value.length);
        field.writeBytes(value);
        return field.toByteArray();
    }

    // Field 4 with wire type 2 is tag byte 0x22, a printable quotation mark, and a payload of at least
    // 32 bytes gives a printable length byte too, so the whole message also decodes as ordinary text.
    private static byte[] printableMessage(String text) {
        byte[] value = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream field = new ByteArrayOutputStream(value.length + 2);
        field.write(PRINTABLE_TAG);
        field.write(value.length);
        field.writeBytes(value);
        return field.toByteArray();
    }

    /**
     * A leaf that records where it was called and marks what it was given, so a reading the transformer
     * discarded is still visible to the assertions.
     */
    private static final class Recorder implements ProtobufLeaf {

        private static final String MARK = "!";
        private final List<Integer> declared;
        private final Collection<List<Integer>> paths;
        private final Collection<byte[]> contents;

        Recorder(Collection<Integer> declared) {
            this.declared = List.copyOf(declared);
            this.paths = new ArrayList<>();
            this.contents = new ArrayList<>();
        }

        @Override
        public ProtobufChange transform(byte[] content, List<Integer> path) {
            this.paths.add(List.copyOf(path));
            this.contents.add(content.clone());
            return ProtobufText.rewritten(content, text -> text + MARK);
        }

        @Override
        public boolean isKnownText(List<Integer> path) {
            return this.declared.equals(path);
        }

        List<List<Integer>> paths() {
            return List.copyOf(this.paths);
        }

        List<byte[]> contents() {
            return List.copyOf(this.contents);
        }
    }
}
