package eu.ciechanowiec.aroxy.client;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * GitHub Copilot CLI's HTTPS forward-proxy conversation. The CLI discovers its API host after
 * authentication, so the transport carries the host families that may be intercepted rather than a
 * single fixed upstream. Requests to every other host remain ordinary CONNECT tunnels.
 *
 * <p>Copilot can select more than one HTTP inference shape. Responses and Anthropic Messages reuse
 * the existing buffered dialects. Chat Completions is identified separately because one event may
 * carry several parallel tool-call argument lanes, which need independent carry buffers.
 *
 * @param hosts       the Copilot host families eligible for TLS interception
 * @param requestBody the JSON fields safe to inspect in every supported inference shape
 * @param responses   the Responses API transport
 * @param messages    the Anthropic Messages API transport
 */
public record CopilotTransport(
    Set<String> hosts,
    RequestBodyPolicy requestBody,
    BufferedTransport responses,
    BufferedTransport messages
) implements ClientTransport {

    private static final List<String> RESPONSE_PATHS = List.of("/responses", "/v1/responses");
    private static final List<String> MESSAGE_PATHS = List.of("/v1/messages");
    private static final List<String> CHAT_PATHS = List.of("/chat/completions", "/v1/chat/completions");

    // copied so the host boundary cannot be changed after the profile is registered
    public CopilotTransport {
        hosts = Set.copyOf(hosts);
    }

    /**
     * Whether a CONNECT destination is a supported Copilot HTTPS host.
     *
     * @param host the requested destination host
     * @param port the requested destination port
     * @return whether aroxy may terminate this tunnel
     */
    public boolean intercepts(String host, int port) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return port > 0 && this.hosts.stream().anyMatch(suffix -> covered(suffix, normalized));
    }

    /**
     * The existing SSE dialect for this path, when it is not Chat Completions.
     *
     * @param path the inference request path
     * @return the matching buffered transport
     */
    public Optional<BufferedTransport> buffered(String path) {
        if (matches(RESPONSE_PATHS, path)) {
            return Optional.of(this.responses);
        }
        return matches(MESSAGE_PATHS, path) ? Optional.of(this.messages) : Optional.empty();
    }

    /**
     * Whether the path carries an OpenAI Chat Completions exchange.
     *
     * @param path the inference request path
     * @return whether parallel Chat Completions lanes apply
     */
    public boolean chatCompletions(String path) {
        return matches(CHAT_PATHS, path);
    }

    private static boolean matches(Iterable<String> candidates, String path) {
        for (String candidate : candidates) {
            if (candidate.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean covered(String suffix, String host) {
        return host.equals(suffix) || host.endsWith("." + suffix);
    }
}
