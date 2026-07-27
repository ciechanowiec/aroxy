package eu.ciechanowiec.aroxy.client;

import java.util.Objects;

/**
 * Everything aroxy needs to know about one client type, in one value: its {@link ClientId} and display
 * name, the {@link Gateway} its listener binds and forwards to, the {@link PathScope} it transforms,
 * its request-body policy, and the {@link SseDialect} its replies are streamed in.
 *
 * <p>This record is the extension point. Supporting another agent means writing one more profile and
 * registering it in the {@link ClientRegistry}. Dropping support means deleting it. Nothing outside the
 * registry enumerates client types, so neither change ripples.
 *
 * @param id          the client this profile describes
 * @param displayName the human-readable name shown by status and configuration views
 * @param gateway     the default listen port and the fixed upstream for this client
 * @param scope       the request paths this client's listener transforms
 * @param requestBody how request bodies are safely scanned
 * @param dialect     the shape of this client's event stream
 */
public record ClientProfile(
    ClientId id,
    String displayName,
    Gateway gateway,
    PathScope scope,
    RequestBodyPolicy requestBody,
    SseDialect dialect
) {

    // Reject an incomplete profile at registration time rather than while binding a listener.
    public ClientProfile {
        Objects.requireNonNull(id);
        Objects.requireNonNull(gateway);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(requestBody);
        Objects.requireNonNull(dialect);
        Objects.requireNonNull(displayName);
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("A client display name must not be blank");
        }
    }
}
