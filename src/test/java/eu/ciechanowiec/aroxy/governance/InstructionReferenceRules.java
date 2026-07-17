package eu.ciechanowiec.aroxy.governance;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Finds backticked references in an instruction file that do not resolve to anything the repository
 * holds. Two shapes are checked, each narrow enough to stay free of false positives on prose. A token
 * counts as a repository path only when it is a plain path whose first segment is a real top-level
 * directory, so runtime paths (for example under a home directory), hosts, and identifiers are ignored.
 * A token counts as a type name only when it is multi-word CamelCase, which no prose word is; a name
 * the instructions invent for a class that was renamed or never written is otherwise invisible, since
 * it carries no slash for the path check to catch.
 *
 * <p>Fenced code blocks are removed before anything is read. A fence is three backticks, so leaving one
 * in place would pair its third backtick with the closing fence's first, swallow the block as a single
 * token, and consume an odd number of backticks; every span after it would then pair the prose between
 * the references instead of the references themselves, and the rest of the file would go unchecked.
 */
@UtilityClass
final class InstructionReferenceRules {

    private static final Pattern FENCE = Pattern.compile("(?s)```.*?```");
    private static final Pattern BACKTICK = Pattern.compile("`([^`]+)`");
    private static final Pattern TYPE_NAME = Pattern.compile("[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+");
    private static final String SPECIAL = "{},<>*:|$ ";
    private static final String SLASH = "/";

    static List<String> unresolved(
        CharSequence content, Collection<String> repositoryDirectories, Predicate<String> exists
    ) {
        return backticked(content)
            .filter(token -> isRepositoryPath(token, repositoryDirectories))
            .filter(token -> !exists.test(stripTrailingSlash(token)))
            .toList();
    }

    /**
     * The backticked type names the content mentions that {@code resolves} cannot account for.
     */
    static List<String> unresolvedTypes(CharSequence content, Predicate<String> resolves) {
        return backticked(content)
            .filter(token -> TYPE_NAME.matcher(token).matches())
            .filter(token -> !resolves.test(token))
            .toList();
    }

    private static Stream<String> backticked(CharSequence content) {
        String prose = FENCE.matcher(content).replaceAll("");
        return BACKTICK.matcher(prose).results()
            .map(match -> match.group(1))
            .distinct();
    }

    private static boolean isRepositoryPath(String token, Collection<String> repositoryDirectories) {
        int slash = token.indexOf('/');
        boolean rooted = slash > 0 && repositoryDirectories.contains(token.substring(0, slash));
        return rooted && isPlainPath(token);
    }

    private static boolean isPlainPath(CharSequence token) {
        return token.chars().noneMatch(InstructionReferenceRules::isSpecial);
    }

    private static boolean isSpecial(int codePoint) {
        return SPECIAL.indexOf(codePoint) >= 0;
    }

    private static String stripTrailingSlash(String token) {
        return token.endsWith(SLASH) ? token.substring(0, token.length() - 1) : token;
    }
}
