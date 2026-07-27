package eu.ciechanowiec.aroxy.proxy;

import java.util.Locale;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The hop-by-hop and restricted request headers that must not be forwarded to the upstream. The
 * {@link java.net.http.HttpClient} manages most of these itself, and forwarding them either corrupts
 * the request or is rejected outright. Every other header (notably the authentication headers) is
 * passed through unchanged.
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
