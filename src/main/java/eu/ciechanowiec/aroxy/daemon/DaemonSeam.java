package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.client.ClientId;
import eu.ciechanowiec.aroxy.client.ClientProfile;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The overrides a {@link Daemon} applies onto its assembled {@link EffectiveConfig}. All are
 * test-only: production pins each client's upstream and binds the ports the configuration asks for, so
 * {@link #production()} overrides nothing. A test overrides the upstreams to reach in-JVM stand-ins
 * instead of the real APIs, and the ports so the daemon binds free ones rather than contending for the
 * production ones with a daemon already serving this machine.
 *
 * <p>The upstreams are held per {@link ClientId} rather than as one address, because a test that
 * proves each listener reaches its own upstream needs to point them at different stand-ins. An empty
 * map keeps every client on its fixed production API.
 *
 * @param upstreams     the upstream to forward to per client, empty to keep the fixed production APIs
 * @param ephemeralPort whether to bind ports chosen by the operating system rather than configured ones
 */
public record DaemonSeam(Map<ClientId, URI> upstreams, boolean ephemeralPort) {

    // copied so the seam cannot be mutated through the map it was built from
    public DaemonSeam {
        upstreams = Map.copyOf(upstreams);
    }

    /**
     * The overrides of a production daemon: none, so the configured ports and the fixed APIs all stand.
     *
     * @return overrides that leave the assembled configuration untouched
     */
    public static DaemonSeam production() {
        return new DaemonSeam(Map.of(), false);
    }

    /**
     * Test-only: binds ports chosen by the operating system, keeping the fixed production upstreams.
     *
     * @return overrides that free the daemon from the production ports
     */
    public static DaemonSeam onEphemeralPort() {
        return new DaemonSeam(Map.of(), true);
    }

    /**
     * Test-only: binds ports chosen by the operating system and forwards every registered client to one
     * in-JVM stand-in.
     *
     * @param upstream the in-JVM upstream every client forwards to instead of its real API
     * @return overrides that free the daemon from both the production ports and the real APIs
     */
    public static DaemonSeam onEphemeralPort(URI upstream) {
        return new DaemonSeam(everyClientAt(upstream), true);
    }

    /**
     * Test-only: binds ports chosen by the operating system and forwards each named client to its own
     * in-JVM stand-in, so a test can tell which listener a request arrived on. A client the map omits
     * keeps its fixed production API.
     *
     * @param upstreams the in-JVM upstream each client forwards to instead of its real API
     * @return overrides that give each named listener its own stand-in
     */
    public static DaemonSeam eachAt(Map<ClientId, URI> upstreams) {
        return new DaemonSeam(upstreams, true);
    }

    private static Map<ClientId, URI> everyClientAt(URI upstream) {
        return ClientRegistry.profiles().stream()
            .collect(Collectors.toUnmodifiableMap(ClientProfile::id, profile -> upstream));
    }

    EffectiveConfig applyTo(EffectiveConfig config) {
        EffectiveConfig pointed = this.pointUpstreams(config);
        return this.ephemeralPort ? pointed.withEphemeralPort() : pointed;
    }

    private EffectiveConfig pointUpstreams(EffectiveConfig config) {
        EffectiveConfig pointed = config;
        for (Map.Entry<ClientId, URI> entry : this.upstreams.entrySet()) {
            pointed = pointed.withUpstream(entry.getKey(), entry.getValue());
        }
        return pointed;
    }
}
