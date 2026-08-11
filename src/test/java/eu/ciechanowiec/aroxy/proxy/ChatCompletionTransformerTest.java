package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Exercises Chat Completions carry buffers independently for text, reasoning, and parallel tools.
 */
class ChatCompletionTransformerTest {

    private static final String TOKEN_HEAD = "<<PII:AWS_";
    private static final String TOKEN_TAIL = "KEY:1>>";
    private static final String CONTENT = "content";

    @Test
    @SneakyThrows
    void restoresATokenSplitAcrossContentEvents() {
        Result result = transform(
            event(indexedContent(TOKEN_HEAD)),
            event(indexedContent(TOKEN_TAIL)),
            "data: [DONE]\n\n"
        );
        assertTrue(result.text().contains(ProxyHarness.SECRET), result.text());
        assertFalse(result.text().contains(TOKEN_HEAD + TOKEN_TAIL), result.text());
        assertEquals(1, result.reversed());
    }

    @Test
    @SneakyThrows
    void keepsParallelToolArgumentLanesSeparate() {
        String first = tools(TOKEN_HEAD, TOKEN_HEAD);
        String second = tools(TOKEN_TAIL, TOKEN_TAIL);
        Result result = transform(event(first), event(second), "data: [DONE]\n\n");
        assertEquals(2, occurrences(result.text(), ProxyHarness.SECRET));
        assertEquals(2, result.reversed());
    }

    @Test
    @SneakyThrows
    void restoresReasoningAndFlushesAnUndelimitedTail() {
        Result result = transform(
            event(direct("reasoning_content", TOKEN_HEAD)),
            "data: " + direct("reasoning_content", TOKEN_TAIL)
        );
        assertTrue(result.text().contains(ProxyHarness.SECRET), result.text());
        assertEquals(1, result.reversed());
    }

    @Test
    @SneakyThrows
    void restoresTheAlternateReasoningField() {
        Result result = transform(
            event(direct("reasoning", TOKEN_HEAD)),
            event(direct("reasoning", TOKEN_TAIL)),
            "data: [DONE]\n\n"
        );
        assertTrue(result.text().contains(ProxyHarness.SECRET), result.text());
        assertEquals(1, result.reversed());
    }

    @Test
    @SneakyThrows
    void passesNonJsonEventsAndEmptyStreams() {
        Result comment = transform(": keepalive\n\n");
        Result malformed = transform("data: {not-json}\n\n");
        Result reversedBraces = transform("} {\n\n");
        assertEquals(": keepalive\n\n", comment.text());
        assertEquals("data: {not-json}\n\n", malformed.text());
        assertEquals("} {\n\n", reversedBraces.text());
        assertEquals("", transform().text());
    }

    @Test
    @SneakyThrows
    void replaysHeldTextAtTheTerminalEvent() {
        Result result = transform(
            event(direct(CONTENT, TOKEN_HEAD)),
            "data: [DONE]\n\n"
        );
        assertTrue(result.text().contains(TOKEN_HEAD), result.text());
    }

    @Test
    @SneakyThrows
    void doesNotFlushHeldTextAtAnOrdinaryMalformedEvent() {
        Result result = transform(
            event(direct(CONTENT, TOKEN_HEAD)),
            "data: {not-json}\n\n",
            event(direct(CONTENT, TOKEN_TAIL)),
            "data: [DONE]\n\n"
        );
        assertTrue(result.text().contains(ProxyHarness.SECRET), result.text());
    }

    @Test
    @SneakyThrows
    void clearsOtherLanesWhenReplayingHeldText() {
        Result result = transform(event(tools(TOKEN_HEAD, TOKEN_HEAD)), "data: [DONE]\n\n");
        assertEquals(2, occurrences(result.text(), TOKEN_HEAD), result.text());
    }

    @Test
    @SneakyThrows
    void transformsMultipleBlocksFromOneChunkAndBareJson() {
        String complete = TOKEN_HEAD + TOKEN_TAIL;
        Result combined = transform(
            event(direct(CONTENT, complete)) + event(direct(CONTENT, complete))
        );
        Result bare = transform(direct(CONTENT, complete) + "\n\n");
        String restored = event(direct(CONTENT, ProxyHarness.SECRET));
        assertEquals(restored + restored, combined.text());
        assertTrue(bare.text().contains(ProxyHarness.SECRET), bare.text());
    }

    @Test
    @SneakyThrows
    void flushesEachPushAndFinishWithoutReplayingPendingBytes() {
        Obfuscator obfuscator = ProxyHarness.obfuscator();
        obfuscator.obfuscate(ProxyHarness.SECRET);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedOutputStream output = new BufferedOutputStream(bytes);
        ChatCompletionTransformer transformer = new ChatCompletionTransformer(output, obfuscator);
        byte[] complete = ": keepalive\n\n".getBytes(StandardCharsets.UTF_8);
        transformer.push(complete, complete.length);
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("keepalive"));
        byte[] partial = ("data: " + direct(CONTENT, TOKEN_HEAD)).getBytes(StandardCharsets.UTF_8);
        transformer.push(partial, partial.length);
        transformer.finish();
        String finished = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(finished.contains(TOKEN_HEAD), finished);
        transformer.finish();
        assertEquals(finished, bytes.toString(StandardCharsets.UTF_8));
    }

    private static String tools(String first, String second) {
        return "{\"choices\":[{\"delta\":{\"tool_calls\":["
            + "{\"index\":0,\"function\":{\"arguments\":\"" + first + "\"}},"
            + "{\"index\":1,\"function\":{\"arguments\":\"" + second + "\"}}]}}]}";
    }

    private static String event(String json) {
        return "data: " + json + "\n\n";
    }

    private static String direct(String field, CharSequence value) {
        return "{\"choices\":[{\"delta\":{\"" + field + "\":\"" + value + "\"}}]}";
    }

    private static String indexedContent(String value) {
        return "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + value + "\"}}]}";
    }

    @SneakyThrows
    private static Result transform(String... chunks) {
        Obfuscator obfuscator = ProxyHarness.obfuscator();
        obfuscator.obfuscate(ProxyHarness.SECRET);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ChatCompletionTransformer transformer = new ChatCompletionTransformer(output, obfuscator);
        for (String chunk : chunks) {
            byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
            transformer.push(bytes, bytes.length);
        }
        transformer.finish();
        return new Result(output.toString(StandardCharsets.UTF_8), transformer.reversedCount());
    }

    private static int occurrences(String text, CharSequence value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }

    private record Result(String text, int reversed) {
    }
}
