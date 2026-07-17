package eu.ciechanowiec.aroxy.config;

import java.net.InetAddress;
import java.net.URI;

/**
 * A typed, immutable view of the {@code [gateway]} stanza. Every component has a sensible default so
 * that an absent key is harmless: the listen host and port, the upstream {@link URI}, and whether
 * secrets are masked in the audit log. The upstream is fixed to {@code https://api.anthropic.com} and
 * is not configurable; {@link #withUpstream} exists only so tests can point the proxy at an in-JVM
 * stand-in.
 *
 * @param listenHost  the local host {@code 127.0.0.1} the proxy binds to
 * @param listenPort  the port the proxy listens on
 * @param upstream    the upstream API base address (always {@code https://api.anthropic.com} in production)
 * @param maskSecrets whether values of sensitive rules are masked in the audit log
 */
public record Settings(
    String listenHost,
    int listenPort,
    URI upstream,
    boolean maskSecrets
) {

    private static final int DEFAULT_PORT = 8788;
    private static final String DEFAULT_UPSTREAM = "https://api.anthropic.com";

    /**
     * The settings used when no configuration file overrides any key.
     *
     * @return the default settings
     */
    public static Settings defaults() {
        String local = InetAddress.getLoopbackAddress().getHostAddress();
        return new Settings(local, DEFAULT_PORT, URI.create(DEFAULT_UPSTREAM), true);
    }

    Settings withPort(int port) {
        return new Settings(this.listenHost, port, this.upstream, this.maskSecrets);
    }

    Settings withUpstream(URI newUpstream) {
        return new Settings(this.listenHost, this.listenPort, newUpstream, this.maskSecrets);
    }

    Settings withMaskSecrets(boolean newMask) {
        return new Settings(this.listenHost, this.listenPort, this.upstream, newMask);
    }
}
