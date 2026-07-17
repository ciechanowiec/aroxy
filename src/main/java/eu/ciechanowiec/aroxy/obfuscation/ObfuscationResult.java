package eu.ciechanowiec.aroxy.obfuscation;

import java.util.List;

/**
 * The outcome of obfuscating one body: the rewritten text with every sensitive value replaced by a
 * token, and the immutable list of matches that fired, in order.
 *
 * @param text    the rewritten body with every sensitive value replaced by a token
 * @param matches the matches that fired, in application order
 */
public record ObfuscationResult(String text, List<ObfuscationMatch> matches) {

    public ObfuscationResult {
        matches = List.copyOf(matches);
    }
}
