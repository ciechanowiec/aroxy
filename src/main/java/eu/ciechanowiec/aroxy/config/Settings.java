package eu.ciechanowiec.aroxy.config;

import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import eu.ciechanowiec.aroxy.client.Gateway;
import eu.ciechanowiec.governance.Justification;
import java.net.InetAddress;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * A typed, immutable view of the {@code [gateway]} stanza. Every component has a sensible default so
 * that an absent key is harmless: the listen host shared by every listener, one {@link Gateway} per
 * registered client, and whether secrets are masked in the audit log.
 *
 * <p>The gateways are held as a map keyed by {@link ClientId} rather than as one component per client,
 * so registering another client in the {@link ClientRegistry} does not reshape this record or anything
 * that reads it.
 *
 * <p>Each {@link Gateway} carries a fixed upstream that no configuration key reaches;
 * {@link #withUpstreams} exists only so tests can point the listeners at an in-JVM stand-in.
 *
 * @param listenHost  the local host {@code 127.0.0.1} every listener binds to
 * @param gateways    the listen port and upstream of each registered client
 * @param maskSecrets whether values of sensitive rules are masked in the audit log
 */
public record Settings(
    String listenHost,
    Map<ClientId, Gateway> gateways,
    boolean maskSecrets
) {

    private static final int EPHEMERAL_PORT = 0;

    // copied so the settings cannot be mutated through the map they were built from
    public Settings {
        gateways = Collections.unmodifiableMap(new LinkedHashMap<>(gateways));
    }

    /**
     * The settings used when no configuration file overrides any key: every registered client on its
     * own default port and fixed upstream.
     *
     * @return the default settings
     */
    public static Settings defaults() {
        String local = InetAddress.getLoopbackAddress().getHostAddress();
        return new Settings(local, ClientRegistry.defaultGateways(), true);
    }

    /**
     * The listener one client binds and forwards to.
     *
     * @param id the client whose gateway is wanted
     * @return that client's effective gateway
     * @throws IllegalStateException if the client has no gateway, which cannot happen for a client the
     *                               {@link ClientRegistry} lists
     */
    public Gateway gateway(ClientId id) {
        return Optional.ofNullable(this.gateways.get(id))
            .orElseThrow(() -> new IllegalStateException("No gateway is configured for " + id));
    }

    @Justification(
        "a local copy, made immutable by the constructor before it escapes"
    )
    @SuppressWarnings(
        "PMD.UseConcurrentHashMap"
    )
    Settings withGateway(ClientId id, Gateway gateway) {
        Map<ClientId, Gateway> merged = new LinkedHashMap<>(this.gateways);
        merged.put(id, gateway);
        return new Settings(this.listenHost, merged, this.maskSecrets);
    }

    Settings withMaskSecrets(boolean newMask) {
        return new Settings(this.listenHost, this.gateways, newMask);
    }

    Settings withUpstreams(URI newUpstream) {
        return this.mapGateways(gateway -> gateway.withUpstream(newUpstream));
    }

    Settings withEphemeralPorts() {
        return this.mapGateways(gateway -> gateway.withPort(EPHEMERAL_PORT));
    }

    private Settings mapGateways(UnaryOperator<Gateway> mapper) {
        Map<ClientId, Gateway> mapped = this.gateways.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> mapper.apply(entry.getValue())));
        return new Settings(this.listenHost, mapped, this.maskSecrets);
    }
}
