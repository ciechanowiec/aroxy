package eu.ciechanowiec.aroxy.daemon;

import java.util.Collection;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Renders the listener list for {@code aroxy status} and {@code aroxy config}: one line per client,
 * naming the address to point that agent at and the upstream it reaches. Client labels are padded to a
 * common width so the addresses line up however many clients this build serves.
 *
 * <p>Listing the listeners rather than naming each client in its own fixed line is what keeps the view
 * from having to change when a client is registered or dropped.
 */
@UtilityClass
public final class GatewayListing {

    private static final String NONE = "(no listeners)";
    private static final String ARROW = "  →  ";
    private static final String GAP = "  ";
    private static final String SCHEME = "http://";

    /**
     * Renders the listener list, one line per client. An empty list renders a placeholder line.
     *
     * @param gateways the listeners to render, in registration order
     * @return the rendered, newline-separated listing
     */
    public static String render(Collection<GatewayAddress> gateways) {
        if (gateways.isEmpty()) {
            return NONE;
        }
        int width = gateways.stream().mapToInt(gateway -> gateway.client().length()).max().orElse(0);
        return gateways.stream().map(gateway -> line(gateway, width)).collect(Collectors.joining("\n"));
    }

    private static String line(GatewayAddress gateway, int width) {
        return pad(gateway.client(), width) + GAP + endpoint(gateway.listen()) + ARROW + gateway.upstream();
    }

    private static String endpoint(String listen) {
        return SCHEME + listen;
    }

    private static String pad(CharSequence label, int width) {
        return label + " ".repeat(width - label.length());
    }
}
