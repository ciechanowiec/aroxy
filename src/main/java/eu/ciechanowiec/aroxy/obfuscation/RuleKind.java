package eu.ciechanowiec.aroxy.obfuscation;

/**
 * The two ways a {@link Rule} can match input: a verbatim regular expression ({@link #REGEX}) or a
 * configured literal value ({@link #LITERAL}) whose spelling variants collapse onto one token.
 */
public enum RuleKind {

    /**
     * A verbatim regular expression matched against the input.
     */
    REGEX,
    /**
     * A configured literal value whose spelling variants collapse onto one token.
     */
    LITERAL
}
