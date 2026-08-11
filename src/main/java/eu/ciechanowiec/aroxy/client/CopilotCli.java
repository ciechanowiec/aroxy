package eu.ciechanowiec.aroxy.client;

import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The native GitHub Copilot CLI profile. Authentication, entitlement checks, and endpoint discovery
 * remain GitHub-owned. Aroxy is an HTTPS forward proxy for the discovered Copilot hosts and transforms
 * only the supported inference routes.
 */
@UtilityClass
public final class CopilotCli {

    /**
     * The stable identity of the GitHub Copilot CLI profile.
     */
    public static final ClientId ID = new ClientId("copilot");

    private static final int DEFAULT_PORT = 8791;
    private static final String REPRESENTATIVE_UPSTREAM = "https://api.githubcopilot.com";
    private static final List<String> INFERENCE_PATHS = List.of(
        "/chat/completions", "/v1/chat/completions", "/responses", "/v1/responses", "/v1/messages"
    );
    private static final Set<String> HOSTS = Set.of(
        "githubcopilot.com",
        "individual.githubcopilot.com",
        "business.githubcopilot.com",
        "enterprise.githubcopilot.com",
        "ghe.com",
        "copilot-proxy.githubusercontent.com",
        "origin-tracker.githubusercontent.com"
    );
    private static final Set<String> OPAQUE = Set.of(
        "encrypted_content",
        "content.image_url",
        "output.image_url",
        "source.data",
        "content.signature",
        "content.data"
    );

    /**
     * The profile registered under {@link #ID}.
     *
     * @return the GitHub Copilot CLI profile
     */
    public static ClientProfile profile() {
        return new ClientProfile(
            ID,
            "GitHub Copilot CLI",
            new Gateway(DEFAULT_PORT, URI.create(REPRESENTATIVE_UPSTREAM)),
            new PathScope(INFERENCE_PATHS, "Copilot inference"),
            new CopilotTransport(
                HOSTS, RequestBodyPolicy.jsonExcept(OPAQUE), CodexCli.transport(), ClaudeCode.transport()
            )
        );
    }
}
