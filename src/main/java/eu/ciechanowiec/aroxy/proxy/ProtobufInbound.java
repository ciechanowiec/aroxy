package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;

/**
 * Defensively copied inbound protobuf transformation result.
 */
@Justification(
    "A record would expose its mutable byte-array component through the generated accessor"
)
@SuppressWarnings(
    "ClassCanBeRecord"
)
final class ProtobufInbound {

    private final byte[] payload;
    private final int reversed;

    ProtobufInbound(byte[] payload, int reversed) {
        this.payload = payload.clone();
        this.reversed = reversed;
    }

    byte[] payload() {
        return this.payload.clone();
    }

    int reversed() {
        return this.reversed;
    }
}
