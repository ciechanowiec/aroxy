package eu.ciechanowiec.aroxy.obfuscation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Stream;

/**
 * Applies one immutable rule snapshot to semantic text. It gathers every candidate against the same
 * input, resolves overlaps leftmost-longest, and only then mints tokens. Gathering first prevents a
 * shorter rule from hiding a longer match merely because it appeared earlier in configuration. A
 * candidate extending past an accepted match contributes its remaining tail, keyed by that exact
 * surface so deobfuscation remains lossless.
 */
final class RuleApplication {

    private static final Comparator<Candidate> PRIORITY = Comparator.comparingInt(Candidate::start)
        .thenComparing(Comparator.comparingInt(Candidate::length).reversed())
        .thenComparingInt(Candidate::ruleOrder);
    private final List<Rule> rules;
    private final Vault vault;

    RuleApplication(Collection<Rule> rules, Vault vault) {
        this.rules = List.copyOf(rules);
        this.vault = vault;
    }

    ObfuscationResult run(String text) {
        List<ObfuscationMatch> matches = new ArrayList<>();
        StringBuilder output = new StringBuilder();
        int cursor = 0;
        for (Candidate candidate : this.resolved(text)) {
            output.append(text, cursor, candidate.start()).append(this.tokenize(candidate, text, matches));
            cursor = candidate.end();
        }
        output.append(text, cursor, text.length());
        return new ObfuscationResult(output.toString(), matches);
    }

    private List<Candidate> resolved(CharSequence text) {
        List<Candidate> accepted = new ArrayList<>();
        int occupiedUntil = -1;
        for (Candidate candidate : this.candidates(text).stream().sorted(PRIORITY).toList()) {
            if (candidate.start() >= occupiedUntil) {
                accepted.add(candidate);
                occupiedUntil = candidate.end();
            } else if (candidate.end() > occupiedUntil) {
                accepted.add(candidate.tailFrom(occupiedUntil));
                occupiedUntil = candidate.end();
            }
        }
        return accepted;
    }

    private List<Candidate> candidates(CharSequence text) {
        List<Candidate> found = new ArrayList<>();
        TokenRegions protectedRegions = TokenRegions.scan(text);
        for (int order = 0; order < this.rules.size(); order++) {
            Rule rule = this.rules.get(order);
            if (rule.enabled()) {
                this.collect(rule, order, text, protectedRegions).forEach(found::add);
            }
        }
        return found;
    }

    private Stream<Candidate> collect(Rule rule, int order, CharSequence text, TokenRegions protectedRegions) {
        Matcher matcher = rule.rulePattern().pattern().matcher(text);
        return matcher.results()
            .map(
                match -> protectedRegions.tokenFreeSpan(match.start(), match.end())
                    .map(
                        span -> new Candidate(
                            rule, order, span[0], span[1], span[0] == match.start() && span[1] == match.end()
                        )
                    )
            ).flatMap(Optional::stream);
    }

    private String tokenize(Candidate candidate, String text, Collection<ObfuscationMatch> sink) {
        String surface = text.substring(candidate.start(), candidate.end());
        Rule rule = candidate.rule();
        RuleLabel label = rule.ruleLabel();
        String key = candidate.complete() ? label.vaultKey(surface) : surface;
        String token = this.vault.tokenFor(key, label.placeholder(), label.sensitive());
        sink.add(new ObfuscationMatch(rule.id(), label.placeholder(), surface, token, label.sensitive()));
        return token;
    }

    private record Candidate(Rule rule, int ruleOrder, int start, int end, boolean complete) {

        int length() {
            return this.end - this.start;
        }

        Candidate tailFrom(int from) {
            return new Candidate(this.rule, this.ruleOrder, from, this.end, false);
        }
    }
}
