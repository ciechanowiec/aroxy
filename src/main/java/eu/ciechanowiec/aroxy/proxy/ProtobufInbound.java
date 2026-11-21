package eu.ciechanowiec.aroxy.proxy;

/**
 * Defensively copied inbound protobuf transformation result.
 */
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
