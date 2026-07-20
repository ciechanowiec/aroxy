package eu.ciechanowiec.aroxy.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The commit-message policy accepts a well-formed Conventional Commit and rejects each way a message
 * can break the rules: a bad header, a too-short or period-ending subject, a junk word, a missing body
 * on a non-trivial change, and an AI-agent attribution marker. A merge or revert header is exempt from
 * the header shape alone, so the attribution ban still reaches it.
 */
class CommitMessageRulesTest {

    private static final DiffStat TRIVIAL = new DiffStat(1, 3);
    private static final DiffStat NON_TRIVIAL = new DiffStat(9, 200);

    @Test
    void acceptsAWellFormedTrivialCommit() {
        CommitMessage message = new CommitMessage("fix(proxy): guard against an empty upstream reply", "");
        assertTrue(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsAnUnknownHeaderType() {
        CommitMessage message = new CommitMessage("improve: make the daemon faster overall", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsATooShortSubject() {
        CommitMessage message = new CommitMessage("fix: too short", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsATrailingPeriod() {
        CommitMessage message = new CommitMessage("docs: document the verification command.", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsAJunkWord() {
        CommitMessage message = new CommitMessage("chore: wip on the reload watcher path", "");
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void requiresABodyForANonTrivialChange() {
        CommitMessage message = new CommitMessage("refactor(config): split the effective config merge", "");
        assertFalse(CommitMessageRules.validate(message, NON_TRIVIAL).isEmpty());
    }

    @Test
    void acceptsANonTrivialChangeThatCarriesABody() {
        CommitMessage message = new CommitMessage(
            "refactor(config): split the effective config merge",
            "The merge grew past the complexity cap, so it moves into smaller value objects."
        );
        assertTrue(CommitMessageRules.validate(message, NON_TRIVIAL).isEmpty());
    }

    @Test
    void rejectsAnAgentAttributionTrailer() {
        CommitMessage message = new CommitMessage(
            "feat(cli): add the reload subcommand to the root",
            "Co-authored-by: Claude <noreply@anthropic.com>"
        );
        assertFalse(CommitMessageRules.validate(message, TRIVIAL).isEmpty());
    }

    @Test
    void rejectsAnAttributionTrailerForEveryAgentNotJustClaude() {
        assertFalse(
            CommitMessageRules.validate(attributed("Co-authored-by: Codex <noreply@openai.com>"), TRIVIAL).isEmpty(),
            "the ban is on naming an agent, not on naming one particular vendor"
        );
        assertFalse(
            CommitMessageRules.validate(attributed("Generated with Codex"), TRIVIAL).isEmpty(),
            "the same trailer form from a different agent"
        );
        assertFalse(
            CommitMessageRules.validate(attributed("codex-session: 0199f0c1-dead-beef"), TRIVIAL).isEmpty(),
            "a session id identifies an agent run as surely as a trailer does"
        );
        assertFalse(
            CommitMessageRules.validate(attributed("https://chatgpt.com/codex"), TRIVIAL).isEmpty(),
            "a bare product page is an advertisement, whichever agent left it"
        );
    }

    /**
     * The ban is on advertising an agent, not on naming the hosts this proxy forwards to - and those
     * hosts are the project's own subject matter. A rule written as "no vendor URL" rather than "no
     * product page" would reject the repository's own honest history, so this pins the difference.
     */
    @Test
    void acceptsAnHonestMentionOfAnUpstreamThisProxyForwardsTo() {
        CommitMessage message = new CommitMessage(
            "feat(client): forward Codex to its ChatGPT backend",
            "Codex signed in with ChatGPT posts to chatgpt.com/backend-api/codex, so the listener "
                + "forwards there rather than to api.anthropic.com."
        );
        assertEquals(
            List.of(), CommitMessageRules.validate(message, NON_TRIVIAL),
            "naming an upstream is not attributing the change to an agent"
        );
    }

    private static CommitMessage attributed(String trailer) {
        return new CommitMessage("feat(cli): add the reload subcommand to the root", trailer);
    }

    @Test
    void exemptsAMergeHeader() {
        CommitMessage message = new CommitMessage("Merge branch 'release' into 'main'", "");
        assertEquals(List.of(), CommitMessageRules.validate(message, NON_TRIVIAL));
    }

    @Test
    void exemptsARevertHeaderFromTheHeaderShape() {
        CommitMessage message = new CommitMessage(
            "Revert \"feat(cli): add the reload subcommand to the root\"",
            "This reverts commit 0123456789abcdef0123456789abcdef01234567."
        );
        assertEquals(List.of(), CommitMessageRules.validate(message, NON_TRIVIAL));
    }

    @Test
    void stillRejectsAnAgentAttributionUnderAnExemptHeader() {
        CommitMessage message = new CommitMessage(
            "Revert \"feat(cli): add the reload subcommand to the root\"",
            "This reverts commit 0123456789abcdef0123456789abcdef01234567.\n\nGenerated with Claude Code"
        );
        assertFalse(
            CommitMessageRules.validate(message, TRIVIAL).isEmpty(),
            "a fixed header form is no licence to name an agent in the body"
        );
    }
}
