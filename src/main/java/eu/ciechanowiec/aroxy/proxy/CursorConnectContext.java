package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import eu.ciechanowiec.aroxy.proxy.CursorHttp.WireFormat;
import java.util.Collection;

/**
 * Request-side Cursor Connect transformation and audit collaborators.
 */
final class CursorConnectContext {

    private final CursorFrameTransformer transformer;
    private final CursorConnectAudit audit;

    CursorConnectContext(ProxyParts parts, ProtobufTransformer protobuf) {
        this.transformer = new CursorFrameTransformer(parts.obfuscator(), protobuf);
        this.audit = new CursorConnectAudit(parts);
    }

    CursorOutboundFrame transform(ConnectJsonFrame frame, WireFormat format) {
        return this.transformer.transform(frame, format);
    }

    String nextStamp() {
        return this.audit.nextStamp();
    }

    void request(String stamp, Collection<ObfuscationMatch> matches) {
        this.audit.request(stamp, matches);
    }

    void response(String stamp, int reversed) {
        this.audit.response(stamp, reversed);
    }

    void requestContinued(String stamp, Collection<ObfuscationMatch> matches, boolean alreadyRedacted) {
        this.audit.requestContinued(stamp, matches, alreadyRedacted);
    }

    void wire(String stamp, BoundedCapture capture) {
        this.audit.wire(stamp, capture);
    }
}
