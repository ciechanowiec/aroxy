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
 * @param loose          for literal rules, also match respellings with separators between letters
 * @param leet           for literal rules, also match digit look-alikes ({@code o}->{@code 0}, ...)
 */
// field count is governed by the project's own PMD cap
@SuppressWarnings(
    "ClassWithTooManyFields"
)
public record RuleSpec(
    String id,
    RuleKind kind,
    String placeholder,
    String patternOrValue,
    String flags,
    boolean sensitive,
    boolean loose,
    boolean leet
) {
}
