package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * One outbound Connect envelope, transformed according to the dialect it was sent in. The
 * end-of-stream envelope carries no payload to transform and has to pass through as it is, while a
 * content envelope has its protobuf leaves rewritten.
 */
class CursorFrameTransformerTest {

    @Test
    void rewritesTheLeavesOfAContentEnvelope() {
        CursorFrameTransformer transformer = transformer();
        byte[] payload = CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET));
        CursorOutboundFrame outbound = transformer.transform(
            new ConnectJsonFrame(0, payload), CursorHttp.WireFormat.PROTOBUF
        );
        String sent = new String(outbound.frame().payload(), StandardCharsets.UTF_8);
        assertTrue(sent.contains("<<PII:AWS_KEY:1>>"), sent);
        assertEquals(1, outbound.matches().size(), "the match is reported for the audit record");
    }

    // The end-of-stream envelope has an empty payload and no leaves. It still has to arrive, because it
    // is what tells the upstream the request is finished.
    @Test
    void passesTheEndOfStreamEnvelopeThroughUnchanged() {
        CursorFrameTransformer transformer = transformer();
        ConnectJsonFrame terminal = new ConnectJsonFrame(ConnectJsonFrame.END_STREAM, new byte[0]);
        CursorOutboundFrame outbound = transformer.transform(terminal, CursorHttp.WireFormat.PROTOBUF);
        assertArrayEquals(new byte[0], outbound.frame().payload(), "nothing is added to it");
        assertEquals(
            ConnectJsonFrame.END_STREAM, outbound.frame().flags(), "it is still the end of the stream"
        );
        assertTrue(outbound.matches().isEmpty(), "an empty payload matches nothing");
    }

    private static CursorFrameTransformer transformer() {
        Obfuscator obfuscator = ProxyHarness.obfuscator();
        return new CursorFrameTransformer(
            obfuscator, new ProtobufTransformer(new ProtobufText(obfuscator))
        );
    }
}
