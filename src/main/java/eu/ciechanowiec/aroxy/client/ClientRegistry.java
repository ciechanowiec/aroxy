package eu.ciechanowiec.aroxy.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The clients this build serves, and the only place that enumerates them. Everything else in the tree
 * iterates this registry: the daemon binds one listener per registered {@link ClientProfile}, the
 * {@code [gateway]} stanza accepts one listen key per profile and validates it, and {@code aroxy
 * status} and {@code aroxy config} render one line per profile.
 *
 * <p>So adding an agent is one {@link ClientProfile} and one line here, and removing one is a
 * deletion. Neither touches the proxy, the configuration parser, or the daemon.
 *
 * <p>Registration order is the order listeners bind and the order the command line renders, so it is
 * kept stable rather than sorted.
 */
@UtilityClass
public final class ClientRegistry {

    private static final List<ClientProfile> PROFILES = validated(
        List.of(ClaudeCode.profile(), CodexCli.profile(), CursorCli.profile())
    );

    /**
     * Every registered profile, in registration order.
     *
     * @return the profiles of the clients this build serves
     */
    public static List<ClientProfile> profiles() {
        return List.copyOf(PROFILES);
    }

    /**
     * The {@code [gateway]} keys that name a listen port, one per registered client, in registration
     * order. Deriving them here rather than listing them in the parser is what gives a newly registered
     * client its configuration key, and the rejection of a typo in it, without further edits.
     *
     * @return the listen keys of the registered clients
     */
    public static List<String> listenKeys() {
        return PROFILES.stream().map(ClientProfile::id).map(ClientId::configKey).toList();
    }

    /**
     * The listener each registered client binds before any configuration file is applied.
     *
     * @return the default gateway of every registered client
     */
    public static Map<ClientId, Gateway> defaultGateways() {
        return PROFILES.stream()
            .collect(Collectors.toUnmodifiableMap(ClientProfile::id, ClientProfile::gateway));
    }

    static List<ClientProfile> validated(Collection<ClientProfile> profiles) {
        rejectDuplicates(profiles, ClientProfile::id, "client id");
        rejectDuplicates(profiles, profile -> profile.gateway().listenPort(), "default port");
        return List.copyOf(profiles);
    }

    private static <T> void rejectDuplicates(
        Collection<ClientProfile> profiles, Function<ClientProfile, T> key, String kind
    ) {
        long distinct = profiles.stream().map(key).distinct().count();
        if (distinct != profiles.size()) {
            throw new IllegalStateException("The client registry contains a duplicate " + kind);
        }
    }
}
