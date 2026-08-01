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

    // The body itself sits under no key, so nothing qualified can match at the root.
    private static final String ROOT = "";

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
        Object transformed = this.transform(parse(body), ROOT);
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

    private Object transform(Object value, String parent) {
        return switch (value) {
            case JSONObject object -> {
                this.transformObject(object, parent);
                yield object;
            }
            case JSONArray array -> {
                this.transformArray(array, parent);
                yield array;
            }
            case String text -> this.transformText(text);
            default -> value;
        };
    }

    private void transformObject(JSONObject object, String parent) {
        for (String key : object.keySet()) {
            object.put(key, this.transformMember(parent, key, object.get(key)));
        }
    }

    private Object transformMember(String parent, String key, Object value) {
        if (this.policy.opaque(parent, key)) {
            return value;
        }
        return this.transform(value, key);
    }

    // An array carries the key of the object that holds it, so a block reached through
    // "content": [...] is qualified by "content" rather than by the array step in between.
    private void transformArray(JSONArray array, String parent) {
        for (int index = 0; index < array.length(); index++) {
            array.put(index, this.transform(array.get(index), parent));
        }
    }

    private String transformText(String text) {
        ObfuscationResult result = this.obfuscator.obfuscate(text);
        this.matches.addAll(result.matches());
        return result.text();
    }
}
