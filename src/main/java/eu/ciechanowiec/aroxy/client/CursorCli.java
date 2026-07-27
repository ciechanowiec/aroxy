package eu.ciechanowiec.aroxy.client;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The {@link ClientProfile} of Cursor CLI, whose primary command is {@code agent}.
 */
@UtilityClass
public final class CursorCli {

    /**
     * The stable identity of the Cursor CLI profile.
     */
    public static final ClientId ID = new ClientId("cursor");

    /**
     * The bidirectional agent stream, and the only path whose label the audit log records.
     */
    public static final String RUN = "/agent.v1.AgentService/Run";

    /**
     * The HTTP/1.1 compatibility form of {@link #RUN}.
     */
    public static final String RUN_SSE = "/agent.v1.AgentService/RunSSE";

    /**
     * The buffered method carrying a nested Run request.
     */
    public static final String APPEND = "/aiserver.v1.BidiService/BidiAppend";

    private static final int DEFAULT_PORT = 8790;
    private static final String UPSTREAM = "https://api2.cursor.sh";
    /*
     * The response frame wraps every event in an interaction update. Within it the assistant's visible
     * text and its thinking each carry their fragment in the first field. These two are the only values
     * Cursor chunks - observed at roughly eleven characters per fragment, shorter than a token, so a
     * token spanning two frames is the rule rather than the exception. Everything else, tool-call
     * arguments included, arrives whole in a single frame.
     */
    private static final int INTERACTION_UPDATE = 1;
    private static final int TEXT_DELTA = 1;
    private static final int THINKING_DELTA = 4;
    private static final int FRAGMENT = 1;

    /**
     * The profile registered under {@link #ID}.
     *
     * @return the Cursor CLI profile
     */
    public static ClientProfile profile() {
        return new ClientProfile(
            ID,
            "Cursor CLI",
            new Gateway(DEFAULT_PORT, URI.create(UPSTREAM)),
            new PathScope(List.of(RUN, RUN_SSE, APPEND), RUN),
            new CursorTransport(
                List.of(RUN, RUN_SSE),
                List.of(
                    List.of(INTERACTION_UPDATE, TEXT_DELTA, FRAGMENT),
                    List.of(INTERACTION_UPDATE, THINKING_DELTA, FRAGMENT)
                ),
                json()
            )
        );
    }

    /*
     * The same stream in its Connect-JSON dialect: each fragment-bearing event names the key its text
     * sits under, and the completion events end a fragment run so a held partial is flushed.
     */
    private static CursorJsonShape json() {
        return new CursorJsonShape(
            Map.of(
                "textDelta", "text",
                "thinkingDelta", "text",
                "partialToolCall", "argsTextDelta",
                "editToolCallDelta", "streamContentDelta",
                "stdout", "content",
                "stderr", "content"
            ),
            Set.of("turnEnded", "toolCallCompleted", "thinkingCompleted"),
            "turnEnded"
        );
    }
}
