package eu.ciechanowiec.aroxy.obfuscation;

import eu.ciechanowiec.governance.Justification;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Builds literal {@link Rule}s from a configured value. The compiler can produce three flavours:
 * an exact-spelling rule, a {@code loose} rule that tolerates separators between characters, and a
 * {@code leet} rule that additionally accepts digit look-alikes ({@code o}->{@code 0},
 * {@code l}->{@code 1}, ...). Every flavour keys its vault entry on the canonical value, so all
 * matched spellings collapse onto a single token.
 */
@UtilityClass
public final class RuleCompiler {

    private static final String SEPARATORS = "[\\s._-]*";
    private static final int ASCII_LIMIT = 128;
    private static final Map<Character, String> LEET_CLASSES = Map.ofEntries(
        Map.entry('a', "[aA4]"),
        Map.entry('b', "[bB8]"),
        Map.entry('e', "[eE3]"),
        Map.entry('g', "[gG9]"),
        Map.entry('i', "[iI1]"),
        Map.entry('l', "[lL1]"),
        Map.entry('o', "[oO0]"),
        Map.entry('s', "[sS5]"),
        Map.entry('t', "[tT7]"),
        Map.entry('z', "[zZ2]")
    );

    /**
     * An exact-spelling literal rule; only the verbatim value matches, restoring to itself.
     */
    @Justification(
        "the single argument value is fixed by the callers"
    )
    @SuppressWarnings(
        "SameParameterValue"
    )
    static Rule exact(String id, String placeholder, String value) {
        Pattern pattern = Pattern.compile(Pattern.quote(value));
        return literalRule(id, placeholder, value, pattern);
    }

    /**
     * A loose (and optionally leet) literal rule, matched case-insensitively.
     */
    @Justification(
        "the single argument value is fixed by the callers"
    )
    @SuppressWarnings(
        "SameParameterValue"
    )
    static Rule loose(String id, String placeholder, LiteralSpec spec) {
        String regex = loosePattern(spec.value(), spec.leet());
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return literalRule(id, placeholder, spec.value(), pattern);
    }

    /**
     * Compiles a declarative {@link RuleSpec} into a ready-to-apply {@link Rule}.
     *
     * @param spec the declarative rule specification to compile
     * @return the compiled, ready-to-apply rule
     */
    public static Rule compile(RuleSpec spec) {
        boolean literal = spec.kind() == RuleKind.LITERAL;
        if (literal) {
            return compileLiteral(spec);
        }
        return compileRegex(spec);
    }

    private static Rule compileLiteral(RuleSpec spec) {
        Pattern pattern = literalPattern(spec);
        RulePattern rulePattern = new RulePattern(pattern);
        RuleLabel label = new RuleLabel(spec.placeholder(), spec.sensitive(), Optional.of(spec.patternOrValue()));
        return Rule.enabled(spec.id(), rulePattern, label);
    }

    private static Pattern literalPattern(RuleSpec spec) {
        boolean expanded = spec.loose() || spec.leet();
        if (expanded) {
            String regex = loosePattern(spec.patternOrValue(), spec.leet());
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        return Pattern.compile(Pattern.quote(spec.patternOrValue()));
    }

    @Justification(
        "the flags are computed at runtime"
    )
    @SuppressWarnings(
        "MagicConstant"
    )
    private static Rule compileRegex(RuleSpec spec) {
        Pattern pattern = Pattern.compile(spec.patternOrValue(), regexFlags(spec.flags()));
        RulePattern rulePattern = new RulePattern(pattern);
        RuleLabel label = RuleLabel.regex(spec.placeholder(), spec.sensitive());
        return Rule.enabled(spec.id(), rulePattern, label);
    }

    private static int regexFlags(String flags) {
        return flagBit(flags, 'i', Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            | flagBit(flags, 's', Pattern.DOTALL)
            | flagBit(flags, 'm', Pattern.MULTILINE);
    }

    private static int flagBit(String flags, char flag, int bit) {
        boolean present = flags.indexOf(flag) >= 0;
        if (present) {
            return bit;
        }
        return 0;
    }

    /**
     * Translates a literal value into a separator-tolerant pattern: whitespace inside the value is
     * dropped and an optional run of spaces, dots, underscores or hyphens is allowed between every
     * adjacent pair of characters.
     */
    static String loosePattern(String value, boolean leet) {
        String compact = value.replaceAll("\\s+", "");
        int[] codePoints = compact.codePoints().toArray();
        StringBuilder pattern = new StringBuilder();
        for (int index = 0; index < codePoints.length; index++) {
            pattern.append(charClass(codePoints[index], leet));
            appendSeparatorBetween(pattern, index, codePoints.length);
        }
        return pattern.toString();
    }

    private static void appendSeparatorBetween(StringBuilder pattern, int index, int length) {
        boolean notLast = index < length - 1;
        if (notLast) {
            pattern.append(SEPARATORS);
        }
    }

    private static String charClass(int codePoint, boolean leet) {
        boolean leetCandidate = leet && codePoint < ASCII_LIMIT;
        if (!leetCandidate) {
            return Pattern.quote(new String(Character.toChars(codePoint)));
        }
        char lower = Character.toLowerCase((char) codePoint);
        return LEET_CLASSES.getOrDefault(lower, Pattern.quote(String.valueOf((char) codePoint)));
    }

    private static Rule literalRule(String id, String placeholder, String canonical, Pattern pattern) {
        RuleLabel label = new RuleLabel(placeholder, false, Optional.of(canonical));
        return Rule.enabled(id, new RulePattern(pattern), label);
    }
}
