package eu.ciechanowiec.aroxy.obfuscation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Applies the configured rules to request text and reverses tokens in response text. Tokens use the
 * JSON-safe, contiguous form {@code <<PII:PLACEHOLDER:N>>}.
 *
 * <p>The engine works on raw body text and stores each match exactly as it appears, so re-inserting
 * it on the response keeps the original JSON escaping intact. Enabled rules are applied in order and
 * their matches accumulate into the {@link ObfuscationResult}. The active rule set may be swapped at
 * runtime via {@link #swapRules(Collection)} so a long-running daemon can reload its configuration without
 * restarting the proxy socket; the swap is published through a {@code volatile} reference.
 */
@SuppressWarnings(
    "PMD.AvoidUsingVolatile"
) // lock-free publication of an immutable rule snapshot on reload
public final class Obfuscator {

    private volatile List<Rule> rules;
    private final Vault vault;

    /**
     * Creates an obfuscator over an initial rule set and the token vault that backs restoration.
     *
     * @param rules the initial enabled rule set, defensively copied
     * @param vault the vault mapping tokens back to their original values
     */
    public Obfuscator(Collection<Rule> rules, Vault vault) {
        this.rules = List.copyOf(rules);
        this.vault = vault;
    }

    /**
     * Atomically replaces the active rule set; in-flight requests finish on their prior snapshot.
     *
     * @param replacement the new rule set, defensively copied
     */
    public void swapRules(Collection<Rule> replacement) {
        this.rules = List.copyOf(replacement);
    }

    /**
     * Replaces every enabled rule's matches with stable tokens.
     *
     * @param body the request body to obfuscate
     * @return the rewritten body together with the matches that fired
     */
    public ObfuscationResult obfuscate(String body) {
        List<ObfuscationMatch> matches = new ArrayList<>();
        String text = body;
        for (Rule rule : this.rules) {
            text = this.applyIfEnabled(rule, text, matches);
        }
        return new ObfuscationResult(text, matches);
    }

    /**
     * Reverses every known token in {@code text}; unknown tokens are left untouched.
     *
     * @param text the response text to restore
     * @return the text with every known token replaced by its original value
     */
    public String deobfuscate(String text) {
        return this.restore(text).text();
    }

    /**
     * Reverses every known token in {@code text}, also reporting how many were actually restored.
     *
     * @param text the response text to restore
     * @return the restored text paired with the count of tokens reversed
     */
    public Reversal restore(String text) {
        if (!text.contains(PiiToken.OPEN)) {
            return new Reversal(text, 0);
        }
        Matcher matcher = PiiToken.TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            count += this.restoreOne(matcher, out);
        }
        matcher.appendTail(out);
        return new Reversal(out.toString(), count);
    }

    private int restoreOne(Matcher matcher, StringBuilder out) {
        Optional<String> original = this.vault.originalFor(matcher.group());
        matcher.appendReplacement(out, Matcher.quoteReplacement(original.orElseGet(matcher::group)));
        return original.isPresent() ? 1 : 0;
    }

    private String applyIfEnabled(Rule rule, String text, Collection<ObfuscationMatch> matches) {
        if (!rule.enabled()) {
            return text;
        }
        return new RuleApplication(rule, this.vault, text).run(matches);
    }
}
