package eu.ciechanowiec.aroxy.governance;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Finds names in a Javadoc comment that denote a type the compiler could resolve from that file, yet
 * are written as prose or as {@code @code} rather than as a link. A type that resolves is always
 * written as a link, so a reader can reach it and the Javadoc tool can check it. {@code @code} is what
 * is left for the things a link cannot reach, such as literals, command names and configuration keys.
 *
 * <p>What a name denotes is not decidable from the text, so the caller decides: it supplies the names
 * that resolve from the file being read, and this class reports the ones it finds unlinked. Three
 * regions are read past, because a name in them is already accounted for: an existing link, a
 * {@code <pre>} sample, which is code rather than prose, and the target of a {@code @param},
 * {@code @throws} or {@code @see} tag, which the Javadoc tool links on its own.
 */
@UtilityClass
final class JavadocLinkRules {

    private static final Pattern JAVADOC = Pattern.compile("(?s)/\\*\\*.*?\\*/");
    private static final Pattern LINK = Pattern.compile("\\{@(?:link|linkplain)\\s+[^}]*}");
    private static final Pattern SAMPLE = Pattern.compile("(?s)<pre>.*?</pre>");
    private static final Pattern TAG_TARGET = Pattern.compile("@(?:param|throws|exception|see)\\s+\\S+");
    private static final Pattern NAME = Pattern.compile("\\b[A-Z][A-Za-z0-9]*\\b");
    private static final String SPACE = " ";

    /**
     * The type names {@code source} mentions in Javadoc without linking them, in encounter order and
     * without repeats.
     */
    static List<String> unlinked(CharSequence source, Predicate<String> resolves) {
        return JAVADOC.matcher(source).results()
            .map(MatchResult::group)
            .flatMap(comment -> namesIn(comment, resolves))
            .distinct()
            .toList();
    }

    private static Stream<String> namesIn(CharSequence comment, Predicate<String> resolves) {
        String prose = accountedFor(comment);
        return NAME.matcher(prose).results()
            .map(MatchResult::group)
            .filter(resolves);
    }

    private static String accountedFor(CharSequence comment) {
        String withoutSamples = SAMPLE.matcher(comment).replaceAll(SPACE);
        String withoutLinks = LINK.matcher(withoutSamples).replaceAll(SPACE);
        return TAG_TARGET.matcher(withoutLinks).replaceAll(SPACE);
    }
}
