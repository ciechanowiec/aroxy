package eu.ciechanowiec.aroxy.config;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The {@code [gateway]} keys present in one configuration file, applied as an overlay onto an
 * existing {@link Settings} so that only the keys this file declares override earlier values.
 */
final class GatewayOverlay {

    private static final String LISTEN = "listen";
    private static final String MASK_SECRETS = "mask_secrets";
    private static final Set<String> KNOWN = Set.of(LISTEN, MASK_SECRETS);

    private final Map<String, String> keys;

    GatewayOverlay(Map<String, String> keys) {
        this.keys = Map.copyOf(keys);
    }

    /**
     * Rejects the file unless every {@code [gateway]} key it declares is one this stanza defines and
     * names a value the proxy can use. Only an absent key is silent, and it keeps the default.
     */
    void validate(String label) {
        this.rejectUnknownKey(label);
        this.rejectUnusable(label, LISTEN, raw -> ConfigValue.asPort(raw).isEmpty(), ConfigValue.portRange());
        this.rejectUnusable(
            label, MASK_SECRETS, raw -> ConfigValue.asBoolean(raw).isEmpty(), ConfigValue.booleanValues()
        );
    }

    private void rejectUnknownKey(String label) {
        this.keys.keySet().stream()
            .filter(key -> !KNOWN.contains(key))
            .findFirst()
            .ifPresent(key -> reject(label, "'" + key + "' is not a [gateway] key; choose one of: " + known()));
    }

    private void rejectUnusable(String label, String key, Predicate<String> unusable, String expected) {
        this.valueOf(key)
            .filter(unusable)
            .ifPresent(raw -> reject(label, "%s '%s' is not %s".formatted(key, raw, expected)));
    }

    private static void reject(String label, String detail) {
        throw new InvalidConfigException(label, detail);
    }

    private static String known() {
        return KNOWN.stream().sorted().collect(Collectors.joining(", "));
    }

    Settings applyTo(Settings base) {
        Settings withListen = this.applyListen(base);
        return this.applyMaskSecrets(withListen);
    }

    private Settings applyListen(Settings base) {
        Optional<Integer> port = this.valueOf(LISTEN).flatMap(ConfigValue::asPort);
        return port.map(base::withPort).orElse(base);
    }

    private Settings applyMaskSecrets(Settings base) {
        Optional<Boolean> value = this.valueOf(MASK_SECRETS).flatMap(ConfigValue::asBoolean);
        return value.map(base::withMaskSecrets).orElse(base);
    }

    private Optional<String> valueOf(String key) {
        return Optional.ofNullable(this.keys.get(key));
    }
}
