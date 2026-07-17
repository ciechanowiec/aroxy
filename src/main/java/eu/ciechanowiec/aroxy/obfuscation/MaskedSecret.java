package eu.ciechanowiec.aroxy.obfuscation;

import lombok.experimental.UtilityClass;

/**
 * Masks a sensitive value for safe display in logs. A long value keeps its first two and last two
 * characters and reports its length (for example {@code AK...LE (20 chars)}); a short value is
 * fully hidden so that no meaningful fragment leaks.
 */
@UtilityClass
public final class MaskedSecret {

    private static final int KEEP = 2;
    private static final int MIN_REVEALABLE = KEEP * 2 + 1;
    private static final String ELLIPSIS = "...";
    private static final String FULL_MASK = "****";

    /**
     * Masks {@code value} for display, revealing only its ends when it is long enough to do so safely.
     *
     * @param value the sensitive value to mask
     * @return the masked rendering; fully hidden when the value is too short to reveal anything
     */
    public static String mask(String value) {
        if (value.length() < MIN_REVEALABLE) {
            return FULL_MASK;
        }
        String head = value.substring(0, KEEP);
        String tail = value.substring(value.length() - KEEP);
        return head + ELLIPSIS + tail + " (" + value.length() + " chars)";
    }
}
