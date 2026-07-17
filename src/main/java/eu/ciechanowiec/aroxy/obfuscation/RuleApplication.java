package eu.ciechanowiec.aroxy.obfuscation;

import java.util.Collection;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

/**
 * Applies a single {@link Rule} to a body, replacing each accepted match with a stable token and
 * recording it. Holds the per-application state (the matcher, the output buffer, the collected
 * matches) so that {@link Obfuscator} can stay small and stateless.
 */
final class RuleApplication {

    private final Rule rule;
    private final Vault vault;
    private final String text;
    private final TokenRegions protectedRegions;

    RuleApplication(Rule rule, Vault vault, String text) {
        this.rule = rule;
        this.vault = vault;
        this.text = text;
        this.protectedRegions = TokenRegions.scan(text);
    }

    String run(Collection<ObfuscationMatch> sink) {
        Matcher matcher = this.rule.rulePattern().pattern().matcher(this.text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            this.handleMatch(matcher, out, sink);
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private void handleMatch(Matcher matcher, StringBuilder out, Collection<ObfuscationMatch> sink) {
        String replacement = this.replacementFor(matcher, sink);
        matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }

    private String replacementFor(Matcher matcher, Collection<ObfuscationMatch> sink) {
        return this.protectedRegions.tokenFreeSpan(matcher.start(), matcher.end())
            .flatMap(window -> JsonSafeSpan.snap(this.text, window[0], window[1]))
            .map(span -> this.tokenize(matcher, span, sink))
            .orElseGet(matcher::group);
    }

    private String tokenize(MatchResult matcher, JsonSafeSpan span, Collection<ObfuscationMatch> sink) {
        String surface = this.text.substring(span.start(), span.end());
        RuleLabel label = this.rule.ruleLabel();
        String token = this.vault.tokenFor(label.vaultKey(surface), label.placeholder(), label.sensitive());
        sink.add(this.matchOf(surface, token));
        return this.wrap(matcher, span, token);
    }

    private String wrap(MatchResult matcher, JsonSafeSpan span, String token) {
        String pre = this.text.substring(matcher.start(), span.start());
        String post = this.text.substring(span.end(), matcher.end());
        return pre + token + post;
    }

    private ObfuscationMatch matchOf(String surface, String token) {
        RuleLabel label = this.rule.ruleLabel();
        return new ObfuscationMatch(this.rule.id(), label.placeholder(), surface, token, label.sensitive());
    }
}
