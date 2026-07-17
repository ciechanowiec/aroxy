package eu.ciechanowiec.aroxy.proxy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Case-insensitive first-value lookup over a response header map, used to read {@code Content-Type}
 * without depending on the exact casing the upstream chose.
 */
@UtilityClass
final class HeaderLookup {

    static Optional<String> first(Map<String, List<String>> headers, String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return headers.entrySet().stream()
            .filter(entry -> matches(entry.getKey(), wanted))
            .flatMap(entry -> entry.getValue().stream())
            .findFirst();
    }

    private static boolean matches(String key, String wanted) {
        return Optional.ofNullable(key)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .filter(wanted::equals)
            .isPresent();
    }
}
