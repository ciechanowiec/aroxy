package eu.ciechanowiec.aroxy.proxy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Recognises a WebSocket upgrade request. This proxy speaks HTTP only: it obfuscates and reverses
 * request and response bodies, which it cannot do across a tunnelled frame protocol.
 *
 * <p>Codex CLI attempts an upgrade before falling back to Server-Sent Events, so refusing well
 * matters. It treats {@code 426 Upgrade Required} as an immediate, clean signal to use SSE instead,
 * whereas any other refusal costs it several visible reconnection attempts first. Answering {@code 426}
 * therefore makes a client that was never configured to skip the attempt degrade quietly rather than
 * appear broken.
 */
@UtilityClass
final class WebSocketUpgrade {

    private static final String UPGRADE = "upgrade";
    private static final String WEBSOCKET = "websocket";

    /**
     * Whether the request asks to leave HTTP for the WebSocket protocol.
     *
     * @param headers the inbound request headers
     * @return whether an upgrade to WebSocket was requested
     */
    static boolean isRequested(Map<String, List<String>> headers) {
        return HeaderLookup.first(headers, UPGRADE)
            .filter(value -> value.toLowerCase(Locale.ROOT).contains(WEBSOCKET))
            .isPresent();
    }
}
