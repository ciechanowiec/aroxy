package eu.ciechanowiec.aroxy.client;

import java.util.Set;

/**
 * How a client's request body can be safely obfuscated. Plain-text mode scans the complete body.
 * JSON mode scans string values independently and leaves the named opaque field values unchanged,
 * which is required for signed or encrypted protocol values.
 *
 * @param json         whether the request body is structured JSON
 * @param opaqueFields JSON field names whose values must never be transformed
 */
public record RequestBodyPolicy(boolean json, Set<String> opaqueFields) {

    public RequestBodyPolicy {
        opaqueFields = Set.copyOf(opaqueFields);
    }

    /**
     * A body that can be scanned as one text value.
     *
     * @return the plain-text policy
     */
    public static RequestBodyPolicy text() {
        return new RequestBodyPolicy(false, Set.of());
    }

    /**
     * A JSON body with protocol fields that must remain opaque.
     *
     * @param fields the field names to exclude from obfuscation at every nesting level
     * @return the structured JSON policy
     */
    public static RequestBodyPolicy jsonExcept(Set<String> fields) {
        return new RequestBodyPolicy(true, fields);
    }

    @Override
    public Set<String> opaqueFields() {
        return Set.copyOf(this.opaqueFields);
    }
}
