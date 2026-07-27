package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import java.util.Collection;

/**
 * Records Cursor Connect request frames as they are transformed and the response when its logical
 * turn ends. The audit trail therefore follows visible stream progress instead of transport teardown.
 */
final class CursorConnectAudit {

    private final AuditLog log;
    private final String endpoint;

    CursorConnectAudit(ProxyParts parts) {
        this.log = parts.audit();
        this.endpoint = parts.profile().scope().auditLabel();
    }

    /**
     * Writes the wire dumps of one streamed exchange. A stream has no single body, so each dump is the
     * concatenated envelopes of its direction, headers included, and is named as the binary it is.
     *
     * @param stamp    the audit stamp shared with the request and response records
     * @param redacted the bytes actually sent upstream
     * @param raw      the bytes the upstream sent back
     */
    void wire(String stamp, byte[] redacted, byte[] raw) {
        this.log.wireRedactedRequest(stamp, redacted, WireBody.BINARY);
        this.log.wireRawResponse(stamp, raw, WireBody.BINARY);
    }

    String nextStamp() {
        return this.log.nextStamp();
    }

    void request(String stamp, Collection<ObfuscationMatch> matches) {
        this.log.request(stamp, this.endpoint, matches);
    }

    void response(String stamp, int reversed) {
        this.log.response(stamp, this.endpoint, reversed, true);
    }
}
