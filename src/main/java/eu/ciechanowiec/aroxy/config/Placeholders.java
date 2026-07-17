package eu.ciechanowiec.aroxy.config;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The closed vocabulary of placeholder names a configuration file may use. Only the placeholders
 * shipped in the bundled {@code default.conf} are permitted, so every token that reaches the model
 * uses a name proven to survive the request/response round trip (free-form names such as
 * {@code COMPANY_D} are rewritten by the model and never restored). A {@link ConfigFile} that names
 * any other placeholder is rejected as broken and is not loaded. The set is documented in the README
 * and kept in step with {@code default.conf} by a test.
 */
@UtilityClass
final class Placeholders {

    private static final Set<String> ALLOWED = Set.of(
        "PRIVKEY", "AWS_KEY", "GH_TOKEN", "SLACK_TOKEN", "API_KEY", "JWT", "DSN", "SECRET",
        "COMPANY", "INTDOMAIN", "PROJECT", "EMAIL", "PHONE", "INTIP", "INTURL"
    );

    /**
     * Rejects the file when any rule names a placeholder outside the allowed vocabulary.
     */
    static void validate(String label, Collection<RuleEntry> rules) {
        firstUnknown(rules).ifPresent(name -> reject(label, name));
    }

    static Set<String> allowed() {
        return ALLOWED;
    }

    private static Optional<String> firstUnknown(Collection<RuleEntry> rules) {
        return rules.stream()
            .map(entry -> entry.spec().placeholder())
            .filter(placeholder -> !ALLOWED.contains(placeholder))
            .findFirst();
    }

    private static void reject(String label, String name) {
        throw new InvalidConfigException(label, detail(name));
    }

    private static String detail(String name) {
        String options = ALLOWED.stream().sorted().collect(Collectors.joining(", "));
        return "placeholder '" + name + "' is not an allowed placeholder; choose one of: " + options;
    }
}
