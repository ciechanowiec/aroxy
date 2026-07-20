package eu.ciechanowiec.aroxy.client;

import java.util.List;

/**
 * The request paths one client's listener transforms. A path is in scope when it equals a prefix or is
 * a sub-resource of one, so {@code /v1/messages} also covers {@code /v1/messages/count_tokens} while
 * the near-miss {@code /v1/messagesX} is not a sub-resource and stays a transparent passthrough.
 *
 * <p>The same scope decides both directions: a request whose body was obfuscated always has its
 * response deobfuscated, so no token can survive into the client.
 *
 * @param prefixes   the path prefixes this client transforms, matched as whole path segments
 * @param auditLabel the endpoint name written to the audit log for this client
 */
public record PathScope(List<String> prefixes, String auditLabel) {

    private static final String SEPARATOR = "/";

    // copied so the scope cannot be mutated through the list it was built from
    public PathScope {
        prefixes = List.copyOf(prefixes);
    }

    /**
     * Whether a request to the given path has its body obfuscated and its reply deobfuscated.
     *
     * @param path the raw request path
     * @return whether this client transforms that path
     */
    public boolean transforms(String path) {
        return this.prefixes.stream().anyMatch(prefix -> covers(prefix, path));
    }

    private static boolean covers(String prefix, String path) {
        return prefix.equals(path) || path.startsWith(prefix + SEPARATOR);
    }
}
