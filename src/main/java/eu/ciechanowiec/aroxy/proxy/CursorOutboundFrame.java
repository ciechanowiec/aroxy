package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import java.util.Collection;
import java.util.List;

/**
 * One transformed outbound Connect envelope and the matches recorded for its audit event.
 */
record CursorOutboundFrame(
    ConnectJsonFrame frame, Collection<ObfuscationMatch> matches
) {

    CursorOutboundFrame {
        matches = List.copyOf(matches);
    }

    @Override
    public Collection<ObfuscationMatch> matches() {
        return List.copyOf(this.matches);
    }
}
