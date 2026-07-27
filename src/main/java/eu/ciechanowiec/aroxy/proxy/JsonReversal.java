package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Reversal;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Restores known aroxy tokens inside JSON string values while retaining valid JSON escaping.
 */
final class JsonReversal {

    private final Obfuscator obfuscator;
    private int count;

    JsonReversal(Obfuscator obfuscator) {
        this.obfuscator = obfuscator;
        this.count = 0;
    }

    Reversal apply(Object value) {
        Object restored = this.transform(value);
        return new Reversal(JSONObject.valueToString(restored), this.count);
    }

    private Object transform(Object value) {
        return switch (value) {
            case JSONObject object -> this.object(object);
            case JSONArray array -> this.array(array);
            case String text -> this.text(text);
            default -> value;
        };
    }

    private JSONObject object(JSONObject object) {
        for (String key : object.keySet()) {
            object.put(key, this.transform(object.get(key)));
        }
        return object;
    }

    private JSONArray array(JSONArray array) {
        for (int index = 0; index < array.length(); index++) {
            array.put(index, this.transform(array.get(index)));
        }
        return array;
    }

    private String text(String text) {
        Reversal reversal = this.obfuscator.restore(text);
        this.count += reversal.count();
        return reversal.text();
    }
}
