package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CodexCli;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives a listener serving the Codex profile over real sockets, against canned replies in Codex's own
 * event vocabulary. Codex dispatches on the {@code type} field of each {@code data:} payload and
 * streams its text under {@code delta}, so none of the Claude Code event names appear here.
 *
 * <p>The case that matters most is {@link #outputItemDoneCarriesTheDeobfuscatedFullText}. A
 * {@code response.output_text.delta} is display-only: Codex persists the item from
 * {@code response.output_item.done} into its conversation history and echoes it back on the next
 * request. Reversing only the deltas would leave a token in that history to egress again on the
 * following turn, which is exactly what this proxy exists to prevent.
 */
class CodexStreamTest {

    private static final String SECRET = ProxyHarness.SECRET;
    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String BREAK = "\n\n";
    private static final String END = "\"}";
    private static final String COMPLETED = "data: {\"type\":\"response.completed\",\"response\":{}}";
    private static final String FAILED = "data: {\"type\":\"response.failed\",\"response\":{}}";

    private static final String SPLIT_STREAM = String.join(
        BREAK,
        "event: response.created\ndata: {\"type\":\"response.created\",\"response\":{}}",
        "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"m1\",\"output_index\":0,"
            + "\"content_index\":0,\"delta\":\"use <<PII:AWS\"}",
        "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"m1\",\"output_index\":0,"
            + "\"content_index\":0,\"delta\":\"_KEY:1>> now\"}",
        COMPLETED
    ) + BREAK;

    private static final String MODEL_CHUNK_STREAM = "\n" + String.join(
        BREAK,
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\"<<\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":4}",
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\"PI\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":5}",
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\"I\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":6}",
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\":\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":7}",
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\"AWS\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":8}",
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\"_KEY\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":9}",
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\":\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":10}",
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\"1\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":11}",
        "data: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\">>\","
            + "\"item_id\":\"m1\",\"output_index\":0,\"sequence_number\":12}",
        COMPLETED
    ) + BREAK;

    private static final String ITEM_DONE_STREAM = String.join(
        BREAK,
        "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"m1\",\"output_index\":0,"
            + "\"content_index\":0,\"delta\":\"use " + TOKEN + END,
        "data: {\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"type\":\"message\","
            + "\"id\":\"m1\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\","
            + "\"text\":\"use " + TOKEN + END + "]}}",
        COMPLETED
    ) + BREAK;

    private static final String TOOL_CALL_DONE_STREAM = String.join(
        BREAK,
        "data: {\"type\":\"response.output_item.done\",\"output_index\":1,\"item\":{\"type\":\"function_call\","
            + "\"name\":\"shell\",\"call_id\":\"call_1\","
            + "\"arguments\":\"{\\\"command\\\":[\\\"echo\\\",\\\"" + TOKEN + "\\\"]}\"}}",
        COMPLETED
    ) + BREAK;

    private static final String CONTENT_PART_DONE_STREAM = String.join(
        BREAK,
        "data: {\"type\":\"response.content_part.done\",\"output_index\":0,\"content_index\":0,"
            + "\"part\":{\"type\":\"output_text\",\"text\":\"use " + TOKEN + END + "}",
        COMPLETED
    ) + BREAK;

    private static final String FUNCTION_ARGUMENT_STREAM = String.join(
        BREAK,
        "data: {\"type\":\"response.function_call_arguments.delta\",\"item_id\":\"f1\","
            + "\"output_index\":0,\"delta\":\"{\\\"value\\\":\\\"<<PII:AWS\"}",
        "data: {\"type\":\"response.function_call_arguments.delta\",\"item_id\":\"f1\","
            + "\"output_index\":0,\"delta\":\"_KEY:1>>\\\"}\"}",
        "data: {\"type\":\"response.function_call_arguments.done\",\"item_id\":\"f1\","
            + "\"output_index\":0,\"arguments\":\"{\\\"value\\\":\\\"" + TOKEN + "\\\"}\"}",
        COMPLETED
    ) + BREAK;

    private static final String REASONING_SUMMARY_STREAM = String.join(
        BREAK,
        "data: {\"type\":\"response.reasoning_summary_text.done\",\"summary_index\":0,"
            + "\"text\":\"considering " + TOKEN + END,
        COMPLETED
    ) + BREAK;

    private static final String HELD_PARTIAL_STREAM = String.join(
        BREAK,
        "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"m1\",\"output_index\":0,"
            + "\"content_index\":0,\"delta\":\"use <<PII:AWS\"}",
        COMPLETED
    ) + BREAK;

    private static final String FAILED_PARTIAL_STREAM = String.join(
        BREAK,
        "data: {\"type\":\"response.output_text.delta\",\"item_id\":\"m1\",\"output_index\":0,"
            + "\"content_index\":0,\"delta\":\"use <<PII:AWS\"}",
        FAILED
    ) + BREAK;

    private static final String REASONING_DELTA_STREAM = String.join(
        BREAK,
        "data: {\"type\":\"response.reasoning_text.delta\",\"content_index\":0,\"delta\":\"maybe " + TOKEN + END,
        COMPLETED
    ) + BREAK;

    @SneakyThrows
    private static ProxyHarness codexHarness(FakeUpstream upstream, Path logs) {
        return new ProxyHarness(upstream, logs, CodexCli.profile());
    }

    @SneakyThrows
    private static HttpResponse<String> drive(ProxyHarness harness) {
        Http.post(harness.base(), Http.RESPONSES, "{\"input\":\"" + SECRET + END);
        return Http.post(harness.base(), Http.RESPONSES, "{\"input\":\"go\"}");
    }

    @Test
    @SneakyThrows
    void requestBodyIsObfuscatedOnTheResponsesPath(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{}"));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            Http.post(harness.base(), Http.RESPONSES, "{\"input\":\"" + SECRET + END);
            assertFalse(upstream.lastBody().contains(SECRET), "the secret must not leave the machine");
            assertTrue(upstream.lastBody().contains("<<PII:AWS_KEY:"), "a token reached the upstream instead");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void encryptedContentRemainsExactWhileOrdinaryInputIsObfuscated(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{}"));
        ProxyHarness harness = codexHarness(upstream, logs);
        String encrypted = "ciphertext-" + SECRET;
        String body = "{\"input\":[{\"type\":\"reasoning\",\"encrypted_content\":\""
            + encrypted + "\"},{\"type\":\"message\",\"content\":\"" + SECRET + "\"}]}";
        try {
            Http.post(harness.base(), Http.RESPONSES, body);
            JSONArray input = new JSONObject(upstream.lastBody()).getJSONArray("input");
            assertEquals(
                encrypted, input.getJSONObject(0).getString("encrypted_content"),
                "opaque ciphertext must reach OpenAI without any substitution"
            );
            assertTrue(
                input.getJSONObject(1).getString("content").contains("<<PII:AWS_KEY:"),
                "ordinary conversation text remains protected"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void responsesSubResourceIsAlsoObfuscated(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{}"));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            Http.post(harness.base(), "/responses/compact", "{\"input\":\"" + SECRET + END);
            assertFalse(upstream.lastBody().contains(SECRET), "a sub-resource of the scope is obfuscated too");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void modelListingIsAPassthrough(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{}"));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            Http.post(harness.base(), "/models", "{\"input\":\"" + SECRET + END);
            assertTrue(
                upstream.lastBody().contains(SECRET),
                "the model listing carries no conversation, so it is forwarded untouched"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void splitTokenAcrossTwoOutputTextDeltasIsReassembled(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(SPLIT_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains(SECRET), "the split token reassembles to the real secret");
            assertFalse(response.body().contains("<<PII:AWS_KEY"), "no token remains in the client stream");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void mislabeledSseStillReassemblesATokenSplitAtEveryModelChunk(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json(MODEL_CHUNK_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains(SECRET), "the fine-grained model chunks reassemble");
            assertFalse(response.body().contains("<<PII:AWS_KEY"), "no token remains in the client stream");
            assertTrue(
                response.headers().firstValue("content-type").orElse("").contains("text/event-stream"),
                "a detected stream is identified correctly to the client despite the upstream header"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void outputItemDoneCarriesTheDeobfuscatedFullText(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(ITEM_DONE_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertFalse(
                response.body().contains("<<PII:AWS_KEY"),
                "the authoritative item is reversed too; a token left here would be persisted into the "
                    + "conversation history and egress again on the next turn"
            );
            assertTrue(response.body().contains("\"output_text\""), "the item's framing is preserved");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void contentPartDoneCarriesTheDeobfuscatedFullText(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(CONTENT_PART_DONE_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains(SECRET), "Codex renders the completed content-part copy");
            assertFalse(
                response.body().contains("<<PII:AWS_KEY"),
                "the content-part event must not retain the token that Codex renders"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void functionCallArgumentsAreDeobfuscatedInTheDoneItem(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(TOOL_CALL_DONE_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains(SECRET), "the tool call runs against the real value");
            assertFalse(response.body().contains("<<PII:AWS_KEY"), "no token remains inside the arguments string");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void splitFunctionCallArgumentsAndTheirDoneCopyAreDeobfuscated(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(FUNCTION_ARGUMENT_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains(SECRET), "the streamed tool arguments use the real value");
            assertFalse(
                response.body().contains("<<PII:AWS_KEY"),
                "neither the deltas nor the complete arguments retain the token"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void reasoningSummaryDoneIsReversedThoughItsTextIsNotUnderDelta(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(REASONING_SUMMARY_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(
                response.body().contains(SECRET),
                "the whole-block pass reverses it without having to know it sits under text, not delta"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void reasoningTextDeltaIsReversed(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(REASONING_DELTA_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains(SECRET), "a reasoning delta streams text and is reversed");
        } finally {
            harness.stop();
        }
    }

    /**
     * A stream that ends mid-token cannot restore it - half a token names no vault entry. What must not
     * happen is the held text being silently dropped, which would truncate the model's reply. So the
     * carry is emitted, and it is emitted by replaying the last fragment rather than by authoring a new
     * event, because Codex's parser requires bookkeeping fields this proxy has no business inventing.
     */
    @Test
    @SneakyThrows
    void heldPartialIsFlushedBeforeResponseCompletedRatherThanDropped(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(HELD_PARTIAL_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains("use "), "the safe part of the fragment is emitted");
            assertTrue(response.body().contains("<<PII:AWS"), "and the held remainder is emitted, not dropped");
            assertTrue(
                response.body().contains("\"item_id\":\"m1\""),
                "the flush replays the last fragment, so it keeps the bookkeeping fields Codex requires"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void heldPartialIsFlushedBeforeAFailedResponse(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(FAILED_PARTIAL_STREAM));
        ProxyHarness harness = codexHarness(upstream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains("<<PII:AWS"), "failure does not drop a held tail");
            assertTrue(
                response.body().indexOf("<<PII:AWS") < response.body().indexOf("response.failed"),
                "the replay is emitted before the terminal failure event"
            );
        } finally {
            harness.stop();
        }
    }
}
