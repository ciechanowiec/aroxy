package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.RequestBodyPolicy;
import eu.ciechanowiec.aroxy.obfuscation.ObfuscationMatch;
import eu.ciechanowiec.aroxy.obfuscation.ObfuscationResult;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Applies an agent profile's body policy without touching opaque protocol values.
 */
final class RequestObfuscation {

    private final RequestBodyPolicy policy;
    private final Obfuscator obfuscator;
    private final List<ObfuscationMatch> matches;

    RequestObfuscation(RequestBodyPolicy policy, Obfuscator obfuscator) {
        this.policy = policy;
        this.obfuscator = obfuscator;
        this.matches = new ArrayList<>();
    }

    ObfuscationResult apply(String body) {
        if (!this.policy.json()) {
            return this.obfuscator.obfuscate(body);
        }
        Object transformed = this.transform(parse(body));
        return new ObfuscationResult(JSONObject.valueToString(transformed), this.matches);
    }

    private static Object parse(String body) {
        JSONTokener tokens = new JSONTokener(body);
        Object value = tokens.nextValue();
        if (tokens.nextClean() != 0) {
            throw new JSONException("Unexpected content after the JSON value");
        }
        return value;
    }

    private Object transform(Object value) {
        return switch (value) {
            case JSONObject object -> {
                this.transformObject(object);
                yield object;
            }
            case JSONArray array -> {
                this.transformArray(array);
                yield array;
            }
            case String text -> this.transformText(text);
            default -> value;
        };
    }

    private void transformObject(JSONObject object) {
        for (String key : object.keySet()) {
            if (!this.policy.opaqueFields().contains(key)) {
                object.put(key, this.transform(object.get(key)));
            }
        }
    }

    private void transformArray(JSONArray array) {
        for (int index = 0; index < array.length(); index++) {
            array.put(index, this.transform(array.get(index)));
        }
    }

    private String transformText(String text) {
        ObfuscationResult result = this.obfuscator.obfuscate(text);
        this.matches.addAll(result.matches());
        return result.text();
    }
}
