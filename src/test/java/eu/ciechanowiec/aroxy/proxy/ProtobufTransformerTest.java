package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.config.ConfigFile;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Rule;
import eu.ciechanowiec.aroxy.obfuscation.Vault;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Verifies recursive protobuf transformation without relying on Cursor's generated descriptors.
 */
@Justification(
    "a unit test of the transformer necessarily names the config, rule and obfuscation types it builds it from"
)
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
class ProtobufTransformerTest {

    private static final int LONG_TEXT_CHARACTERS = 140;
    private static final int PRINTABLE_TAG_PADDING = 22;
    private static final int PRINTABLE_TAG = 0x22;
    private static final byte DANGLING_VARINT = (byte) 0x80;
    private static final int EXCESSIVE_NESTING = 66;
    private static final int MAX_DEPTH = 64;
    private static final int VARINT_SHIFT = 7;
    private static final int VARINT_MASK = 0x7F;
    private static final int VARINT_CONTINUATION = 0x80;
    private static final String CONFIG = """
        [gateway]
        [rule]
        id = company
        type = literal
        placeholder = COMPANY
        value = ExampleCorp
        [rule]
        id = project
        type = literal
        placeholder = COMPANY
        value = Helios
        """;

    @Test
    void obfuscatesAndRestoresTextInsideNestedMessages() {
        ProtobufTransformer transformer = transformer();
        byte[] original = field(field("Ask ExampleCorp about ExampleCorp"));
        ProtobufTransformer.Outbound outbound = transformer.outbound(original);
        String redacted = new String(outbound.payload(), StandardCharsets.UTF_8);
        assertEquals(2, outbound.matches().size());
        assertTrue(redacted.contains("<<PII:COMPANY:1>>"), redacted);
        ProtobufTransformer.Inbound inbound = transformer.inbound(outbound.payload());
        assertArrayEquals(original, inbound.payload());
        assertEquals(2, inbound.reversed());
    }

    // A nested message whose own bytes read as printable text must still be recursed into. Rewriting it
    // as one opaque string would leave its inner length prefix describing the pre-obfuscation size, so
    // a malformed message would reach the upstream while aroxy's own round trip still looked correct.
    @Test
    void recursesIntoNestedMessagesThatDecodeAsPrintableText() {
        ProtobufTransformer transformer = transformer();
        byte[] inner = fieldFour("ExampleCorp" + "x".repeat(PRINTABLE_TAG_PADDING));
        byte[] original = field(inner);
        byte[] sent = transformer.outbound(original).payload();
        assertEquals(1, transformer.outbound(original).matches().size());
        assertEquals(sent.length - 2, sent[1], "the outer length prefix describes the rewritten value");
        assertEquals(sent.length - 4, sent[3], "the inner length prefix describes the rewritten value");
        assertArrayEquals(original, transformer.inbound(sent).payload());
    }

    // "Ask about Helios" is ordinary prose that also happens to parse as a well-formed protobuf message
    // (field 8 fixed64, field 4 varint, field 13 fixed32, consuming exactly its 16 bytes). Preferring
    // the message reading here would walk straight past the secret and let it egress in the clear.
    @Test
    void obfuscatesTextThatAlsoParsesAsAWellFormedMessage() {
        ProtobufTransformer transformer = transformer();
        byte[] original = field("Ask about Helios");
        byte[] sent = transformer.outbound(original).payload();
        assertArrayEquals(field("Ask about <<PII:COMPANY:1>>"), sent);
        assertArrayEquals(original, transformer.inbound(sent).payload());
    }

    // The streaming restorer routes every leaf through this entry point, so a binary leaf sitting
    // beside a streamed one must pass through untouched rather than being mangled into UTF-8.
    @Test
    void rewritesOnlyTheLeavesThatDecodeAsText() {
        byte[] binary = Base64.getDecoder().decode("/w==");
        assertArrayEquals(binary, ProtobufText.rewritten(binary, text -> text + "!").bytes());
        assertTrue(
            ProtobufText.rewritten("hi".getBytes(StandardCharsets.UTF_8), text -> text + "!")
                .changed()
        );
        assertArrayEquals(
            "hi".getBytes(StandardCharsets.UTF_8),
            ProtobufText.rewritten("hi".getBytes(StandardCharsets.UTF_8), text -> text).bytes()
        );
    }

    // A varint whose continuation bit is set on the last available byte runs off the end of the buffer.
    // That has to be reported as a malformed payload, not as an index error escaping the parser.
    @Test
    void rejectsAVarintThatRunsPastTheEndOfTheBuffer() {
        ProtobufTransformer transformer = transformer();
        assertThrows(
            ConnectProtocolException.class, () -> transformer.outbound(new byte[]{DANGLING_VARINT})
        );
    }

    // Field number 0 does not exist on the wire. Without the check, tag byte 0x02 reads as a
    // zero-length field-0 string and the whole message parses as valid rather than being rejected.
    @Test
    void rejectsFieldNumberZero() {
        ProtobufTransformer transformer = transformer();
        assertThrows(
            ConnectProtocolException.class, () -> transformer.outbound(new byte[]{2, 0})
        );
    }

    // The depth cap must admit a message that sits exactly at it. Refusing there would silently push a
    // legitimate nesting down the text branch, where its length prefixes would go stale.
    @Test
    void transformsAMessageNestedExactlyAtTheDepthCap() {
        ProtobufTransformer transformer = transformer();
        byte[] nested = field("about ExampleCorp");
        for (int depth = 1; depth < MAX_DEPTH; depth++) {
            nested = field(nested);
        }
        ProtobufTransformer.Outbound transformed = transformer.outbound(nested);
        assertEquals(1, transformed.matches().size(), "the leaf at the cap is still transformed");
        assertArrayEquals(nested, transformer.inbound(transformed.payload()).payload());
    }

    // One level of nesting, so a sign error in the reversal tally cannot cancel itself out the way it
    // does across an even number of levels.
    @Test
    void countsReversalsThroughASingleLevelOfNesting() {
        ProtobufTransformer transformer = transformer();
        byte[] original = field("about ExampleCorp");
        byte[] sent = transformer.outbound(original).payload();
        assertEquals(1, transformer.inbound(sent).reversed(), "one token, counted once, positive");
    }

    // Past the depth cap the walk stops recursing and reads the value as text instead. That has to
    // happen strictly beyond the cap: giving up one level early would rewrite a still-nested message as
    // one opaque string, leaving its inner length prefix describing the pre-obfuscation size.
    @Test
    void recursesThroughTheDeepestPermittedNesting() {
        ProtobufTransformer transformer = transformer();
        byte[] nested = field("about ExampleCorp");
        byte[] expected = field("about <<PII:COMPANY:1>>");
        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            nested = field(nested);
            expected = field(expected);
        }
        assertArrayEquals(
            expected, transformer.outbound(nested).payload(),
            "every level is rewritten as a message, so every length prefix is recomputed"
        );
    }

    // A binary leaf is returned untouched on the way back too, not only on the way out.
    @Test
    void leavesANonTextLeafUntouchedWhenRestoring() {
        ProtobufTransformer transformer = transformer();
        byte[] binary = field(bytes("/w=="));
        assertArrayEquals(binary, transformer.inbound(binary).payload());
        assertEquals(0, transformer.inbound(binary).reversed(), "nothing was restored in binary bytes");
    }

    @Test
    void preservesUnknownScalarsAndPackedBytesExactly() {
        ProtobufTransformer transformer = transformer();
        byte[] original = Base64.getDecoder().decode("CJYBEQECAwQFBgcIGgMBAgMlCQoLDA==");
        assertArrayEquals(original, transformer.outbound(original).payload());
        assertArrayEquals(original, transformer.inbound(original).payload());
    }

    @Test
    void rejectsMalformedTopLevelMessages() {
        ProtobufTransformer transformer = transformer();
        assertThrows(ConnectProtocolException.class, () -> transformer.outbound(new byte[]{10, 5, 1}));
        assertThrows(ConnectProtocolException.class, () -> transformer.inbound(new byte[]{0}));
    }

    @Test
    void handlesLongTextAndBinaryLengthDelimitedValues() {
        ProtobufTransformer transformer = transformer();
        byte[] longText = field(
            "ExampleCorp " + "x".repeat(LONG_TEXT_CHARACTERS) + "\n\r\t"
        );
        ProtobufTransformer.Outbound transformed = transformer.outbound(longText);
        assertTrue(
            new String(transformed.payload(), StandardCharsets.UTF_8)
                .contains("<<PII:COMPANY:1>>")
        );
        assertArrayEquals(longText, transformer.inbound(transformed.payload()).payload());
        byte[] leadingControl = field('\1' + "binary");
        byte[] invalidUtf8 = field(bytes("/w=="));
        assertArrayEquals(leadingControl, transformer.outbound(leadingControl).payload());
        assertArrayEquals(invalidUtf8, transformer.outbound(invalidUtf8).payload());
    }

    @Test
    void rejectsUnsupportedAndOverlongWireValues() {
        ProtobufTransformer transformer = transformer();
        assertThrows(ConnectProtocolException.class, () -> transformer.outbound(bytes("Cw==")));
        assertThrows(
            ConnectProtocolException.class,
            () -> transformer.outbound(bytes("CoCAgIAI"))
        );
        byte[] overlong = bytes("gICAgICAgICAgA==");
        assertThrows(ConnectProtocolException.class, () -> transformer.outbound(overlong));
        byte[] nested = field("leaf");
        for (int depth = 0; depth < EXCESSIVE_NESTING; depth++) {
            nested = field(nested);
        }
        assertArrayEquals(nested, transformer.outbound(nested).payload());
    }

    private static ProtobufTransformer transformer() {
        List<Rule> rules = EffectiveConfig.merge(List.of(ConfigFile.parse("test", CONFIG))).rules();
        Obfuscator obfuscator = new Obfuscator(rules, new Vault(Optional.empty()));
        return new ProtobufTransformer(new ProtobufText(obfuscator));
    }

    private static byte[] field(String text) {
        return field(text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] field(byte[] value) {
        ByteArrayOutputStream field = new ByteArrayOutputStream(value.length + 3);
        field.write(10);
        writeVarint(field, value.length);
        field.writeBytes(value);
        return field.toByteArray();
    }

    // Field 4 with wire type 2 is tag byte 0x22, a printable quotation mark, and a payload of at least
    // 32 bytes gives a printable length byte too: the whole message then decodes as ordinary text.
    private static byte[] fieldFour(String text) {
        byte[] value = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream field = new ByteArrayOutputStream(value.length + 2);
        field.write(PRINTABLE_TAG);
        writeVarint(field, value.length);
        field.writeBytes(value);
        return field.toByteArray();
    }

    private static void writeVarint(ByteArrayOutputStream output, int value) {
        int remaining = value;
        while ((remaining & ~VARINT_MASK) != 0) {
            output.write((remaining & VARINT_MASK) | VARINT_CONTINUATION);
            remaining >>>= VARINT_SHIFT;
        }
        output.write(remaining);
    }

    private static byte[] bytes(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}
