package eu.ciechanowiec.aroxy.client;

import java.util.Set;

/**
 * How a client's request body can be safely obfuscated. Plain-text mode scans the complete body.
 * JSON mode scans string values independently and leaves the named opaque field values unchanged,
 * which is required for a signed, encrypted, or binary protocol value.
 *
 * <p>An opaque name comes in two forms. A bare {@code field} is opaque wherever it occurs, which is
 * what a name unique to the protocol needs. A qualified {@code parent.field} is opaque only where the
 * field sits in an object reached under that parent, which is what a name the protocol shares with
 * ordinary content needs: an attachment's bytes travel under {@code data}, and so does a parameter
 * named {@code data} of a tool the agent declares, whose description is prose the rules must still
 * scan.
 *
 * @param json         whether the request body is structured JSON
 * @param opaqueFields the field names, bare or {@code parent.field}-qualified, whose values must
 *                     never be transformed
 */
public record RequestBodyPolicy(boolean json, Set<String> opaqueFields) {

    private static final char QUALIFIER = '.';

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
     * @param fields the field names to exclude from obfuscation, each bare or {@code parent.field}
     * @return the structured JSON policy
     */
    public static RequestBodyPolicy jsonExcept(Set<String> fields) {
        return new RequestBodyPolicy(true, fields);
    }

    /**
     * Whether a field's value is protocol-opaque at the place it was reached.
     *
     * @param parent the key of the object the field sits in, empty at the root of the body
     * @param field  the name of the field being considered
     * @return whether the value must be forwarded exactly as it arrived
     */
    public boolean opaque(String parent, String field) {
        return this.opaqueFields.contains(field)
            || this.opaqueFields.contains(parent + QUALIFIER + field);
    }

    @Override
    public Set<String> opaqueFields() {
        return Set.copyOf(this.opaqueFields);
    }
}
