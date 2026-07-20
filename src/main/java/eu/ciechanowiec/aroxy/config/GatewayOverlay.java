package eu.ciechanowiec.aroxy.config;

import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code [gateway]} keys present in one configuration file, applied as an overlay onto an
 * existing {@link Settings} so that only the keys this file declares override earlier values.
 *
 * <p>The listen keys are not listed here: they are derived from the {@link ClientRegistry}, one per
 * registered {@link ClientProfile}. Registering another client therefore gives it a listen key, the
 * rejection of a typo in that key, and the rejection of an unusable port, without editing this class.
 */
final class GatewayOverlay {

    private static final String MASK_SECRETS = "mask_secrets";

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
        ClientRegistry.listenKeys().forEach(key -> this.rejectUnusablePort(label, key));
        this.rejectUnusable(
            label, MASK_SECRETS, raw -> ConfigValue.asBoolean(raw).isEmpty(), ConfigValue.booleanValues()
        );
    }

    private void rejectUnknownKey(String label) {
        this.keys.keySet().stream()
            .filter(key -> !known().contains(key))
            .findFirst()
            .ifPresent(key -> reject(label, "'" + key + "' is not a [gateway] key; choose one of: " + listed()));
    }

    private void rejectUnusablePort(String label, String key) {
        this.rejectUnusable(label, key, raw -> ConfigValue.asPort(raw).isEmpty(), ConfigValue.portRange());
    }

    private void rejectUnusable(String label, String key, Predicate<String> unusable, String expected) {
        this.valueOf(key)
            .filter(unusable)
            .ifPresent(raw -> reject(label, "%s '%s' is not %s".formatted(key, raw, expected)));
    }

    private static void reject(String label, String detail) {
        throw new InvalidConfigException(label, detail);
    }

    private static Set<String> known() {
        return Stream.concat(ClientRegistry.listenKeys().stream(), Stream.of(MASK_SECRETS))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String listed() {
        return known().stream().sorted().collect(Collectors.joining(", "));
    }

    Settings applyTo(Settings base) {
        Settings withListen = this.applyListenKeys(base);
        return this.applyMaskSecrets(withListen);
    }

    private Settings applyListenKeys(Settings base) {
        Settings applied = base;
        for (ClientProfile profile : ClientRegistry.profiles()) {
            applied = this.applyListen(applied, profile.id());
        }
        return applied;
    }

    private Settings applyListen(Settings base, ClientId id) {
        return this.valueOf(id.configKey())
            .flatMap(ConfigValue::asPort)
            .map(port -> base.withGateway(id, base.gateway(id).withPort(port)))
            .orElse(base);
    }

    private Settings applyMaskSecrets(Settings base) {
        Optional<Boolean> value = this.valueOf(MASK_SECRETS).flatMap(ConfigValue::asBoolean);
        return value.map(base::withMaskSecrets).orElse(base);
    }

    private Optional<String> valueOf(String key) {
        return Optional.ofNullable(this.keys.get(key));
    }
}
