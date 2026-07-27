package eu.ciechanowiec.aroxy.proxy;

import java.util.Locale;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The hop-by-hop and restricted request headers that must not be forwarded to the upstream. The
 * {@link java.net.http.HttpClient} manages most of these itself, and forwarding them either corrupts
 * the request or is rejected outright. Every other header (notably the authentication headers) is
 * passed through unchanged.
 *
 * <p>Both HTTP clients consult this one list, {@link Upstream} for the buffered routes and
 * {@link CursorDuplexUpstream} for Cursor's stream. A per-client list would let one of them forward a
 * header the other drops, and which headers reach a vendor is a property of the proxy rather than of the
 * client library it happens to be using.
 */
@UtilityClass
final class RestrictedHeaders {

    private static final Set<String> NAMES = Set.of(
        "host", "content-length", "connection", "accept-encoding",
        "transfer-encoding", "upgrade", "expect"
    );
    private static final String PROXY_PREFIX = "proxy-";

    static boolean isForbidden(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return NAMES.contains(lower) || lower.startsWith(PROXY_PREFIX);
    }
}
