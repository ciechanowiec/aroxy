package eu.ciechanowiec.aroxy.proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Copies the upstream response headers onto the downstream exchange, dropping the headers this proxy
 * manages itself or that the body transform invalidates ({@code transfer-encoding},
 * {@code content-length}, {@code connection}, {@code keep-alive}, {@code content-encoding}) and the
 * HTTP/2 pseudo-headers (names starting with {@code :}).
 */
@UtilityClass
final class ResponseHeaderCopier {

    private static final Set<String> DROPPED = Set.of(
        "transfer-encoding", "content-length", "connection", "keep-alive", "content-encoding"
    );

    static void copy(Map<String, List<String>> from, Map<String, List<String>> to) {
        from.forEach((name, values) -> copyOne(name, values, to));
    }

    private static void copyOne(String name, List<String> values, Map<String, List<String>> to) {
        if (isCopyable(name)) {
            to.put(name, new ArrayList<>(values));
        }
    }

    private static boolean isCopyable(String name) {
        return !isPseudo(name) && !DROPPED.contains(name.toLowerCase(Locale.ROOT));
    }

    private static boolean isPseudo(String name) {
        return name.startsWith(":");
    }
}
