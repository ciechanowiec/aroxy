package eu.ciechanowiec.aroxy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins how each client's dialect classifies an event block. Both sides of every predicate are covered,
 * because a block wrongly classified as plain silently stops being deobfuscated: the stream still
 * flows and the client still renders, but a token reaches the user or, worse, the model's history.
 */
class SseDialectTest {

    private static String event(String type) {
        return "data: {\"type\":\"" + type + "\",\"delta\":\"x\"}";
    }

    @ParameterizedTest
    @CsvSource(
        {
            "response.output_text.delta,             FRAGMENT",
            "response.reasoning_summary_text.delta,  FRAGMENT",
            "response.reasoning_text.delta,          FRAGMENT",
            "response.function_call_arguments.delta, FRAGMENT",
            "response.custom_tool_call_input.delta,  FRAGMENT",
            "response.code_interpreter_call_code.delta, FRAGMENT",
            "response.output_item.done,              WHOLE",
            "response.output_text.done,              WHOLE",
            "response.content_part.done,             WHOLE",
            "response.reasoning_summary_text.done,   WHOLE",
            "response.function_call_arguments.done,  WHOLE",
            "response.custom_tool_call_input.done,   WHOLE",
            "response.completed,                     PLAIN",
            "response.failed,                        PLAIN",
            "response.incomplete,                    PLAIN",
            "response.created,                       PLAIN",
            "response.output_item.added,             PLAIN"
        }
    )
    void codexClassifiesEachEventType(String type, SseKind expected) {
        assertEquals(expected, CodexCli.profile().dialect().classify(event(type)), type);
    }

    @Test
    void codexStreamsEveryFragmentUnderTheDeltaKey() {
        SseDialect dialect = CodexCli.profile().dialect();
        assertTrue(
            dialect.fragments().stream().allMatch(rule -> "\"delta\":\"".equals(rule.fieldKey())),
            "every Codex fragment carries its text under delta, unlike the done events"
        );
    }

    @Test
    void everyCodexStreamEndingEventIsTerminal() {
        SseDialect dialect = CodexCli.profile().dialect();
        assertTrue(dialect.isTerminal(event("response.completed")), "the stream end flushes a held partial");
        assertTrue(dialect.isTerminal(event("response.failed")), "a failed stream also flushes");
        assertTrue(dialect.isTerminal(event("response.incomplete")), "an incomplete stream also flushes");
        assertFalse(dialect.isTerminal(event("response.created")), "the stream start does not");
        assertFalse(
            dialect.isTerminal(event("response.output_text.delta")),
            "a fragment is not terminal; flushing there would break the very reassembly it feeds"
        );
    }

    @ParameterizedTest
    @CsvSource(
        {
            "content_block_delta.text_delta,        FRAGMENT",
            "content_block_delta.input_json_delta,  FRAGMENT",
            "content_block_stop,                    PLAIN",
            "message_stop,                          PLAIN",
            "ping,                                  PLAIN"
        }
    )
    void claudeCodeClassifiesEachEventType(String shape, SseKind expected) {
        assertEquals(expected, ClaudeCode.profile().dialect().classify(claudeCodeBlock(shape)), shape);
    }

    private static String claudeCodeBlock(String shape) {
        int dot = shape.indexOf('.');
        if (dot < 0) {
            return "data: {\"type\":\"" + shape + "\"}";
        }
        return "data: {\"type\":\"" + shape.substring(0, dot) + "\",\"index\":0,"
            + "\"delta\":{\"type\":\"" + shape.substring(dot + 1) + "\",\"text\":\"x\"}}";
    }

    @Test
    void claudeCodeDeclaresNoWholeBlocksBecauseItsDeltasAreAuthoritative() {
        assertTrue(
            ClaudeCode.profile().dialect().wholeMarkers().isEmpty(),
            "nothing repeats the streamed text, so there is no second copy to reverse"
        );
    }

    @Test
    void aMessageDeltaIsDeliberatelyNotTerminalForClaudeCode() {
        assertFalse(
            ClaudeCode.profile().dialect().isTerminal("data: {\"type\":\"message_delta\",\"usage\":{}}"),
            "it arrives after the block closed, so replaying a fragment there would land under a "
                + "content block whose stop has already passed"
        );
    }

    @Test
    void aFragmentRuleNeedsEveryOneOfItsMarkers() {
        FragmentRule rule = new FragmentRule(Set.of("\"a\"", "\"b\""), "\"t\":\"");
        SseDialect dialect = new SseDialect(List.of(rule), Set.of(), Set.of());
        assertEquals(SseKind.FRAGMENT, dialect.classify("{\"a\":1,\"b\":2}"), "both markers present");
        assertEquals(SseKind.PLAIN, dialect.classify("{\"a\":1}"), "one marker alone does not match");
    }

    @Test
    void aDialectWithNoRulesClassifiesEverythingAsPlain() {
        SseDialect empty = new SseDialect(List.of(), Set.of(), Set.of());
        assertEquals(SseKind.PLAIN, empty.classify("data: {\"type\":\"anything\"}"), "nothing to match");
        assertTrue(empty.fragment("data: {}").isEmpty(), "and no rule is located");
        assertFalse(empty.isTerminal("data: {}"), "and nothing is terminal");
    }
}
