package eu.ciechanowiec.aroxy.client;

import java.net.URI;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The {@link ClientProfile} of Codex CLI signed in with ChatGPT, forwarded to the ChatGPT backend.
 * Signing in with ChatGPT rather than with an API key is what puts the traffic on this host. The
 * bearer token it sends is forwarded unchanged, and the separate token refresh it performs never
 * reaches this listener.
 *
 * <p>Its stream dispatches on the {@code type} field of each {@code data:} payload, so the
 * {@code event:} line carries no meaning here. Model text, reasoning, and tool input stream under the
 * key {@code delta}.
 *
 * <p>Complete {@code *.done} events are {@link SseKind#WHOLE}. Declaring them is what stops a token
 * leaking back out: Codex persists completed message and tool items into its conversation history and
 * echoes them back in the next request. Reversing only the deltas would leave tokens in that history.
 * The whole-block pass also handles complete values whose key is {@code text}, {@code arguments}, or
 * {@code input} rather than {@code delta}, without teaching the transformer those field names.
 *
 * <p>{@code response.completed}, {@code response.failed}, and {@code response.incomplete} end a stream.
 * There is no separate sentinel event.
 *
 * <p>The request fields declared opaque are encoded rather than written. {@code encrypted_content}
 * carries the reasoning ciphertext this profile echoes back. An attached image travels under
 * {@code content.image_url}, while an image returned by a tool is replayed under
 * {@code output.image_url}. Codex sends both image forms as a base64 data URL. A substitution in one is
 * what the API reports as an invalid base64-encoded value, naming the path rather than the cause. A
 * substitution in a remote URL would likewise leave a resource the vendor cannot dereference.
 * Whichever form arrives, the field has to leave as it came.
 */
@UtilityClass
public final class CodexCli {

    /**
     * The stable identity of the Codex CLI profile.
     */
    public static final ClientId ID = new ClientId("codex");

    private static final int DEFAULT_PORT = 8789;
    private static final String UPSTREAM = "https://chatgpt.com/backend-api/codex";
    private static final String RESPONSES = "/responses";
    private static final String DELTA_KEY = "\"delta\":\"";
    private static final Set<String> ENCODED = Set.of(
        "encrypted_content", "content.image_url", "output.image_url"
    );

    /**
     * The profile registered under {@link #ID}.
     *
     * @return the Codex CLI profile
     */
    public static ClientProfile profile() {
        return new ClientProfile(
            ID,
            "Codex CLI",
            new Gateway(DEFAULT_PORT, URI.create(UPSTREAM)),
            new PathScope(List.of(RESPONSES), RESPONSES),
            new BufferedTransport(RequestBodyPolicy.jsonExcept(ENCODED), dialect())
        );
    }

    private static SseDialect dialect() {
        return new SseDialect(
            List.of(
                streamed("response.output_text.delta"),
                streamed("response.reasoning_summary_text.delta"),
                streamed("response.reasoning_text.delta"),
                streamed("response.function_call_arguments.delta"),
                streamed("response.custom_tool_call_input.delta"),
                streamed("response.code_interpreter_call_code.delta")
            ),
            Set.of(
                quoted("response.output_item.done"),
                quoted("response.output_text.done"),
                quoted("response.content_part.done"),
                quoted("response.reasoning_summary_text.done"),
                quoted("response.reasoning_text.done"),
                quoted("response.function_call_arguments.done"),
                quoted("response.custom_tool_call_input.done"),
                quoted("response.code_interpreter_call_code.done")
            ),
            Set.of(
                quoted("response.completed"), quoted("response.failed"), quoted("response.incomplete")
            )
        );
    }

    private static FragmentRule streamed(String type) {
        return new FragmentRule(Set.of(quoted(type)), DELTA_KEY);
    }

    private static String quoted(String type) {
        return '"' + type + '"';
    }
}
