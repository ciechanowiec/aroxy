package eu.ciechanowiec.aroxy.obfuscation;

import eu.ciechanowiec.airness.Justification;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * Applies the configured rules to request text and reverses tokens in response text. Tokens use the
 * JSON-safe, contiguous form {@code <<PII:PLACEHOLDER:N>>}.
 *
 * <p>The engine works on semantic text: structured request bodies are parsed before their string
 * values reach it, so backslashes in paths and source code are content rather than serialization
 * syntax. Competing matches use leftmost-longest resolution, with configuration order breaking an
 * exact tie. The active rule set may be swapped at runtime via {@link #swapRules(Collection)} so a
 * long-running daemon can reload its configuration without restarting the proxy socket. The swap is
 * published through a {@code volatile} reference.
 */
@Justification(
    "lock-free publication of an immutable rule snapshot on reload"
)
@SuppressWarnings(
    "PMD.AvoidUsingVolatile"
)
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
     * Atomically replaces the active rule set. In-flight requests finish on their prior snapshot.
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
        RuleApplication application = new RuleApplication(this.rules, this.vault);
        String text = body;
        ObfuscationResult pass = application.run(text);
        while (!pass.matches().isEmpty()) {
            text = pass.text();
            matches.addAll(pass.matches());
            pass = application.run(text);
        }
        return new ObfuscationResult(text, matches);
    }

    /**
     * Reverses every known token in {@code text}. Unknown tokens are left untouched.
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
}
