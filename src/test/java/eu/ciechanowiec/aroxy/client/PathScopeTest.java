package eu.ciechanowiec.aroxy.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins which request paths each registered client transforms. A path is matched as whole segments, so
 * a sub-resource is in scope and a name that merely starts with the prefix is not: that near-miss must
 * stay a transparent passthrough rather than have its body rewritten.
 */
class PathScopeTest {

    @Test
    void claudeCodeCoversMessagesAndItsSubResources() {
        PathScope scope = ClaudeCode.profile().scope();
        assertTrue(scope.transforms("/v1/messages"), "the exact path is transformed");
        assertTrue(scope.transforms("/v1/messages/count_tokens"), "a sub-resource is transformed");
        assertFalse(scope.transforms("/v1/messagesX"), "a near-miss is not a sub-resource");
        assertFalse(scope.transforms("/v1/models"), "an unrelated path is a passthrough");
    }

    @Test
    void codexCoversResponsesAndItsSubResourcesButNotModels() {
        PathScope scope = CodexCli.profile().scope();
        assertTrue(scope.transforms("/responses"), "the exact path is transformed");
        assertTrue(scope.transforms("/responses/compact"), "a sub-resource is transformed");
        assertFalse(scope.transforms("/responsesX"), "a near-miss is not a sub-resource");
        assertFalse(
            scope.transforms("/models"),
            "the model listing carries no conversation, so it passes through untouched"
        );
    }

    @Test
    void aScopeWithNoPrefixesTransformsNothing() {
        PathScope scope = new PathScope(List.of(), "/none");
        assertFalse(scope.transforms("/v1/messages"), "no prefix matches anything");
    }

    @Test
    void eachClientKeepsItsOwnPathsOutOfTheOthersScope() {
        assertFalse(
            ClaudeCode.profile().scope().transforms("/responses"),
            "the Claude Code listener does not transform a Codex path"
        );
        assertFalse(
            CodexCli.profile().scope().transforms("/v1/messages"),
            "the Codex listener does not transform a Claude Code path"
        );
    }
}
