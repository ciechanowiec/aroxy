package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.RequestBodyPolicy;
import eu.ciechanowiec.aroxy.obfuscation.ObfuscationResult;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Transforms the JSON payloads carried by Connect envelopes.
 */
final class ConnectJsonTransformer {

    private static final RequestBodyPolicy JSON = RequestBodyPolicy.jsonExcept(Set.of());
    private final Obfuscator obfuscator;

    ConnectJsonTransformer(Obfuscator obfuscator) {
        this.obfuscator = obfuscator;
    }

    OutboundFrame outbound(ConnectJsonFrame frame) {
        String json = new String(frame.payload(), StandardCharsets.UTF_8);
        try {
            ensureObject(json);
            ObfuscationResult result = new RequestObfuscation(JSON, this.obfuscator).apply(json);
            return new OutboundFrame(
                new ConnectJsonFrame(frame.flags(), result.text().getBytes(StandardCharsets.UTF_8)),
                result
            );
        } catch (JSONException exception) {
            ConnectProtocolException failure = new ConnectProtocolException(
                "Invalid JSON in Connect request envelope: "
                    + Objects.toString(exception.getMessage(), "parse failed")
            );
            failure.initCause(exception);
            throw failure;
        }
    }

    private static void ensureObject(String json) {
        JSONTokener tokens = new JSONTokener(json);
        Object value = tokens.nextValue();
        if (!(value instanceof JSONObject) || tokens.nextClean() != 0) {
            throw new JSONException("Connect envelope is not one JSON object");
        }
    }

    public record OutboundFrame(ConnectJsonFrame frame, ObfuscationResult result) {
    }
}
