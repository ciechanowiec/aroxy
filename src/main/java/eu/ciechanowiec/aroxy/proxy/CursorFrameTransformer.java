package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.proxy.CursorHttp.WireFormat;
import java.util.List;

/**
 * Transforms one outbound Cursor Connect envelope according to its negotiated wire format.
 */
final class CursorFrameTransformer {

    private final ConnectJsonTransformer json;
    private final ProtobufTransformer protobuf;

    CursorFrameTransformer(Obfuscator obfuscator, ProtobufTransformer protobuf) {
        this.json = new ConnectJsonTransformer(obfuscator);
        this.protobuf = protobuf;
    }

    CursorOutboundFrame transform(ConnectJsonFrame frame, WireFormat format) {
        CursorHttp.ensureUncompressed(frame);
        return format == WireFormat.JSON
            ? this.json(frame)
            : this.protobuf(frame);
    }

    private CursorOutboundFrame json(ConnectJsonFrame frame) {
        ConnectJsonTransformer.OutboundFrame transformed = this.json.outbound(frame);
        return new CursorOutboundFrame(
            transformed.frame(), transformed.result().matches()
        );
    }

    private CursorOutboundFrame protobuf(ConnectJsonFrame frame) {
        if (frame.terminal()) {
            return new CursorOutboundFrame(frame, List.of());
        }
        ProtobufTransformer.Outbound transformed = this.protobuf.outbound(frame.payload());
        return new CursorOutboundFrame(
            new ConnectJsonFrame(frame.flags(), transformed.payload()), transformed.matches()
        );
    }
}
