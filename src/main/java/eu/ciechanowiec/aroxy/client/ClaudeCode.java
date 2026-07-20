package eu.ciechanowiec.aroxy.client;

import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The {@link ClientProfile} of Claude Code, forwarded to the Claude API.
 *
 * <p>Its stream carries model text in a {@code content_block_delta}, either as a {@code text_delta}
 * (whose text is under {@code text}) or as an {@code input_json_delta} (under {@code partial_json}).
 * The authoritative text is the concatenation of those deltas, so this dialect declares no
 * {@link SseKind#WHOLE} blocks.
 *
 * <p>Only {@code content_block_stop} and {@code message_stop} are terminal. A {@code message_delta}
 * carries {@code stop_reason} and {@code usage} and arrives after the block has already closed, so
 * flushing a held partial there would replay a fragment under an index whose
 * {@code content_block_stop} has already passed.
 */
@UtilityClass
public final class ClaudeCode {

    /**
     * The stable identity of the Claude Code profile.
     */
    public static final ClientId ID = new ClientId("claude");

    private static final int DEFAULT_PORT = 8788;
    private static final String UPSTREAM = "https://api.anthropic.com";
    private static final String MESSAGES = "/v1/messages";
    private static final String BLOCK_DELTA = "\"content_block_delta\"";

    /**
     * The profile registered under {@link #ID}.
     *
     * @return the Claude Code profile
     */
    public static ClientProfile profile() {
        return new ClientProfile(
            ID,
            "Claude Code",
            new Gateway(DEFAULT_PORT, URI.create(UPSTREAM)),
            new PathScope(List.of(MESSAGES), MESSAGES),
            dialect()
        );
    }

    private static SseDialect dialect() {
        return new SseDialect(
            List.of(
                new FragmentRule(Set.of(BLOCK_DELTA, "\"text_delta\""), "\"text\":\""),
                new FragmentRule(Set.of(BLOCK_DELTA, "\"input_json_delta\""), "\"partial_json\":\"")
            ),
            Set.of(),
            Set.of("\"content_block_stop\"", "\"message_stop\"")
        );
    }
}
