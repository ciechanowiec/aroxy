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
     * <p>The traffic counted is the capture's full total in each direction, not the length of its
     * possibly capped dump.
     *
     * @param stamp   the audit stamp shared with the request and response records
     * @param capture both directions of the exchange as sent upstream and received back
     */
    void wire(String stamp, BoundedCapture capture) {
        this.log.wireRedactedRequest(stamp, capture.sentBytes(), WireBody.BINARY, capture.sentTotal());
        this.log.wireRawResponse(stamp, capture.receivedBytes(), WireBody.BINARY, capture.receivedTotal());
    }

    String nextStamp() {
        return this.log.nextStamp();
    }

    void request(String stamp, Collection<ObfuscationMatch> matches) {
        this.log.request(stamp, this.endpoint, matches);
    }

    void requestContinued(String stamp, Collection<ObfuscationMatch> matches, boolean alreadyRedacted) {
        this.log.requestContinued(stamp, this.endpoint, matches, alreadyRedacted);
    }

    void response(String stamp, int reversed) {
        this.log.response(stamp, this.endpoint, reversed, true);
    }
}
