package eu.ciechanowiec.aroxy.config;

import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Reads the scalar values a configuration key may carry. Every reader returns an empty result rather
 * than a fallback when the text does not name a usable value, so the caller decides that the file is
 * broken; a value is never quietly coerced into a default. Kept separate so the stanza readers stay
 * focused on structure rather than value coercion.
 */
@UtilityClass
final class ConfigValue {

    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final char PORT_SEPARATOR = ':';
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    /**
     * Reads a boolean. Returns empty for anything other than {@code true} or {@code false} in any case.
     */
    static Optional<Boolean> asBoolean(String raw) {
        boolean known = TRUE.equalsIgnoreCase(raw) || FALSE.equalsIgnoreCase(raw);
        return known ? Optional.of(TRUE.equalsIgnoreCase(raw)) : Optional.empty();
    }

    /**
     * Reads the port from a {@code listen} value. Any host portion is ignored: the proxy always binds
     * the local interface {@code 127.0.0.1}, so only the part after the last {@code :} (or the whole
     * value when there is no {@code :}) is parsed. Returns empty when the port is missing or out of range.
     */
    static Optional<Integer> asPort(String raw) {
        String portText = raw.substring(raw.lastIndexOf(PORT_SEPARATOR) + 1).strip();
        return parsePort(portText);
    }

    /**
     * Names the range {@link #asPort} accepts, so a caller rejecting a value can say what it wanted.
     */
    static String portRange() {
        return "a port between " + MIN_PORT + " and " + MAX_PORT;
    }

    /**
     * Names the values {@link #asBoolean} accepts, so a caller rejecting a value can say what it wanted.
     */
    static String booleanValues() {
        return TRUE + " or " + FALSE;
    }

    private static Optional<Integer> parsePort(String portText) {
        try {
            return inRange(Integer.parseInt(portText));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> inRange(int port) {
        boolean valid = port >= MIN_PORT && port <= MAX_PORT;
        return valid ? Optional.of(port) : Optional.empty();
    }
}
