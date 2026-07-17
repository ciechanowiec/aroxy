package eu.ciechanowiec.aroxy.obfuscation;

/**
 * The outcome of reversing tokens in a piece of text: the text with every <em>known</em> token
 * restored to its original value, and the count of tokens that were actually restored. Tokens whose
 * value is unknown to the vault are left in place and are not counted, so the count reflects real
 * reversals rather than every token-shaped substring.
 *
 * @param text  the text with known tokens restored
 * @param count how many tokens were actually reversed
 */
public record Reversal(String text, int count) {
}
