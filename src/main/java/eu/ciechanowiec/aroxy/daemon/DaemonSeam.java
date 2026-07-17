package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import java.net.URI;
import java.util.Optional;

/**
 * The overrides a {@link Daemon} applies onto its assembled {@link EffectiveConfig}. Both are
 * test-only: production pins the upstream to {@code https://api.anthropic.com} and binds the port the
 * configuration asks for, so {@link #production()} overrides neither. A test overrides the upstream to
 * reach an in-JVM stand-in instead of the real API, and the port so the daemon binds a free port
 * rather than contending for the production one with a daemon already serving this machine.
 *
 * @param upstream      the upstream to forward to, or empty to keep the fixed production API
 * @param ephemeralPort whether to bind a port chosen by the operating system rather than the configured one
 */
public record DaemonSeam(Optional<URI> upstream, boolean ephemeralPort) {

    /**
     * The overrides of a production daemon: none, so the configured port and the fixed API both stand.
     *
     * @return overrides that leave the assembled configuration untouched
     */
    public static DaemonSeam production() {
        return new DaemonSeam(Optional.empty(), false);
    }

    /**
     * Test-only: binds a port chosen by the operating system, keeping the fixed production upstream.
     *
     * @return overrides that free the daemon from the production port
     */
    public static DaemonSeam onEphemeralPort() {
        return new DaemonSeam(Optional.empty(), true);
    }

    /**
     * Test-only: binds a port chosen by the operating system and forwards to an in-JVM stand-in.
     *
     * @param upstream the in-JVM upstream to forward to instead of the real API
     * @return overrides that free the daemon from both the production port and the real API
     */
    public static DaemonSeam onEphemeralPort(URI upstream) {
        return new DaemonSeam(Optional.of(upstream), true);
    }

    EffectiveConfig applyTo(EffectiveConfig config) {
        EffectiveConfig pointed = this.upstream.map(config::withUpstream).orElse(config);
        return this.ephemeralPort ? pointed.withEphemeralPort() : pointed;
    }
}
