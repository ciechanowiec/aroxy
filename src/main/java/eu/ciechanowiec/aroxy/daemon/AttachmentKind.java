package eu.ciechanowiec.aroxy.daemon;

/**
 * The provenance of a contributing configuration file shown by {@code status} and {@code config
 * list}: the always-present bundled {@code default.conf}, a persistent registry attachment, or a
 * temporary this-session attachment. {@link #label()} is the fixed-width tag printed before each
 * path so the three kinds align in a column.
 */
public enum AttachmentKind {

    /**
     * The always-present bundled {@code default.conf}.
     */
    DEFAULT("default   "),
    /**
     * A persistent registry attachment, loaded on every start.
     */
    PERSISTENT("persistent"),
    /**
     * A temporary this-session attachment, cleared on the next start.
     */
    TEMPORARY("temporary ");

    private final String label;

    AttachmentKind(String label) {
        this.label = label;
    }

    /**
     * The fixed-width tag printed before each path so the three kinds align in a column.
     *
     * @return the column-aligned label for this kind
     */
    public String label() {
        return this.label;
    }
}
