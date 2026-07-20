package eu.ciechanowiec.aroxy.client;

import java.util.regex.Pattern;

/**
 * The stable, configuration-safe identity of one agent client. This is a value rather than an enum so
 * the set of supported clients is defined only by the {@link ClientRegistry}: adding a client does not
 * require extending a second closed type.
 *
 * @param slug the short lower-case name used in configuration and internal maps
 */
public record ClientId(String slug) {

    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_-]*");
    private static final String LISTEN_PREFIX = "listen_";

    // Reject an id that cannot form an unambiguous [gateway] key.
    public ClientId {
        if (!VALID.matcher(slug).matches()) {
            throw new IllegalArgumentException("A client id must match " + VALID.pattern() + ": " + slug);
        }
    }

    /**
     * The {@code [gateway]} key naming the port this client's listener binds.
     *
     * @return the configuration key that sets this client's listen port
     */
    public String configKey() {
        return LISTEN_PREFIX + this.slug;
    }
}
