package eu.ciechanowiec.aroxy.obfuscation;

/**
 * A flat, declarative description of one obfuscation rule as read from a configuration file, before
 * it is compiled into a {@link Rule} by {@link RuleCompiler#compile(RuleSpec)}. A record is used so
 * that the wide constructor stays within the project's parameter budget (the canonical constructor
 * of a record is exempt from the parameter-count limit).
 *
 * @param id             unique rule name, also shown in the audit log
 * @param kind           whether the rule matches a regex or a literal value
 * @param placeholder    the label that appears inside the token {@code <<PII:PLACEHOLDER:N>>}
 * @param patternOrValue the regex pattern (for {@link RuleKind#REGEX}) or the literal text (for
 *                       {@link RuleKind#LITERAL})
 * @param flags          any combination of {@code i} (ignore case), {@code s} (dot matches newline),
 *                       {@code m} (multiline)
 * @param sensitive      whether the value is masked in the audit log
 * @param respelling     for literal rules, the respellings matched beyond the verbatim value
 */
public record RuleSpec(
    String id,
    RuleKind kind,
    String placeholder,
    String patternOrValue,
    String flags,
    boolean sensitive,
    Respelling respelling
) {

    /**
     * Whether a literal rule also matches respellings with separators between letters.
     *
     * @return {@code true} when loose matching is on
     */
    public boolean loose() {
        return this.respelling.loose();
    }

    /**
     * Whether a literal rule also matches digit look-alikes.
     *
     * @return {@code true} when leet matching is on
     */
    public boolean leet() {
        return this.respelling.leet();
    }
}
