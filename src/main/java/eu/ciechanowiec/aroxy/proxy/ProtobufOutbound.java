package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import java.util.Collection;
import java.util.List;

/**
 * Defensively copied outbound protobuf transformation result.
 */
final class ProtobufOutbound {

    private final byte[] payload;
    private final Collection<ObfuscationMatch> matches;

    ProtobufOutbound(byte[] payload, Collection<ObfuscationMatch> matches) {
        this.payload = payload.clone();
        this.matches = List.copyOf(matches);
    }

    byte[] payload() {
        return this.payload.clone();
    }

    Collection<ObfuscationMatch> matches() {
        return List.copyOf(this.matches);
    }
}
