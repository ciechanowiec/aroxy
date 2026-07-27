package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationResult;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Reversal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Transforms the UTF-8 text leaves of a protobuf message. A length-delimited value that does not
 * decode as UTF-8 is binary and is returned untouched.
 */
final class ProtobufText {

    private final Obfuscator obfuscator;

    ProtobufText(Obfuscator obfuscator) {
        this.obfuscator = obfuscator;
    }

    ProtobufChange outbound(byte[] content) {
        Optional<String> decoded = decoded(content);
        if (decoded.isEmpty()) {
            return ProtobufChange.unchanged(content);
        }
        ObfuscationResult result = this.obfuscator.obfuscate(decoded.orElseThrow());
        return new ProtobufChange(
            result.text().getBytes(StandardCharsets.UTF_8),
            result.matches(), 0, !result.matches().isEmpty()
        );
    }

    ProtobufChange inbound(byte[] content) {
        Optional<String> decoded = decoded(content);
        if (decoded.isEmpty()) {
            return ProtobufChange.unchanged(content);
        }
        Reversal result = this.obfuscator.restore(decoded.orElseThrow());
        return new ProtobufChange(
            result.text().getBytes(StandardCharsets.UTF_8),
            List.of(), result.count(), result.count() > 0
        );
    }

    /**
     * Applies an arbitrary text transform to a length-delimited value, leaving a value that is not
     * UTF-8 untouched. The reversal count is left at zero because an arbitrary transform is not
     * required to be a restoration and has no count to report. A caller that runs one over a fragment
     * run keeps the count in the carry buffer that spans those fragments, which is the only place it
     * can be kept, the run being longer than any single value
     *
     * @param content   the raw bytes of the value
     * @param transform the transform applied to the decoded text
     * @return the transformation outcome
     */
    static ProtobufChange rewritten(byte[] content, UnaryOperator<String> transform) {
        Optional<String> decoded = decoded(content);
        if (decoded.isEmpty()) {
            return ProtobufChange.unchanged(content);
        }
        String original = decoded.orElseThrow();
        String result = transform.apply(original);
        return new ProtobufChange(
            result.getBytes(StandardCharsets.UTF_8), List.of(), 0, !result.equals(original)
        );
    }

    private static Optional<String> decoded(byte[] content) {
        Optional<String> decoded = Optional.empty();
        try {
            decoded = Optional.of(
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString()
            );
        } catch (CharacterCodingException _) {
            // Binary length-delimited values are not semantic text.
        }
        return decoded;
    }
}
