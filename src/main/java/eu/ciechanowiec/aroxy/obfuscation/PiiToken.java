package eu.ciechanowiec.aroxy.obfuscation;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Defines the JSON-safe token form {@code <<PII:PLACEHOLDER:N>>} used to stand in for a sensitive
 * value, and the helpers needed to build, recognise and parse such tokens.
 *
 * <p>The form contains no backslashes or double quotes, so substituting it into a JSON string never
 * introduces an escape that could break the surrounding document.
 */
@UtilityClass
final class PiiToken {

    static final String OPEN = "<<PII:";
    static final String CLOSE = ">>";
    static final Pattern TOKEN = Pattern.compile("<<PII:[A-Za-z0-9_]+:\\d+>>");
    private static final char SEPARATOR = ':';

    static String build(String placeholder, int index) {
        return OPEN + placeholder + SEPARATOR + index + CLOSE;
    }

    /**
     * Parses the numeric counter out of a token, returning {@code 0} for an unparsable token.
     */
    static int indexOf(String token) {
        int colon = token.lastIndexOf(SEPARATOR);
        int end = token.indexOf(CLOSE, Math.max(colon, 0));
        boolean malformed = colon < 0 || end < 0;
        if (malformed) {
            return 0;
        }
        return parseOrZero(token.substring(colon + 1, end));
    }

    private static int parseOrZero(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException _) {
            return 0;
        }
    }
}
