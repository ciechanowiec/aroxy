package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the SSE transformer reassembles a token whose two halves land in consecutive
 * {@code content_block_delta} events: the first delta ends with {@code <<PII:AWS} and the next starts
 * with {@code _KEY:1>>}. End to end, the client's stream must show the real secret, never the token.
 */
class SseReassemblyTest {

    private static final String SECRET = ProxyHarness.SECRET;
    private static final String JSON_PAYLOAD = "{\\\"k\\\":\\\"<<PII:AWS_KEY:1>>\\\"}";

    private static final String SPLIT_STREAM = String.join(
        "\n\n",
        "event: message_start\ndata: {\"type\":\"message_start\"}",
        "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":0,"
            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"use <<PII:AWS\"}}",
        "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":0,"
            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"_KEY:1>> now\"}}",
        "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}",
        "event: message_stop\ndata: {\"type\":\"message_stop\"}"
    ) + "\n\n";

    private static final String CRLF_SPLIT_STREAM = String.join(
        "\r\n\r\n",
        "event: content_block_delta\r\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":0,"
            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"use <<PII:AWS\"}}",
        "event: content_block_delta\r\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":0,"
            + "\"delta\":{\"type\":\"text_delta\",\"text\":\"_KEY:1>> now\"}}",
        "event: content_block_stop\r\ndata: {\"type\":\"content_block_stop\",\"index\":0}",
        "event: message_stop\r\ndata: {\"type\":\"message_stop\"}"
    ) + "\r\n\r\n";

    private static final String JSON_DELTA_STREAM = String.join(
        "\n\n",
        "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":1,"
            + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"" + JSON_PAYLOAD + "\"}}",
        "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":1}"
    ) + "\n\n";

    private static final String JSON_HELD_PARTIAL_STREAM = String.join(
        "\n\n",
        "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"index\":1,"
            + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"<<PII:AWS\"}}",
        "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":1}"
    ) + "\n\n";

    @SneakyThrows
    private static HttpResponse<String> drive(ProxyHarness harness) {
        mintToken(harness);
        return Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"go\"}");
    }

    @SneakyThrows
    private static void mintToken(ProxyHarness harness) {
        Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"" + SECRET + "\"}");
    }

    @Test
    @SneakyThrows
    void inputJsonDeltaTokenIsReversed(@TempDir Path logs) {
        FakeUpstream stream = new FakeUpstream(CannedReply.sse(JSON_DELTA_STREAM));
        ProxyHarness harness = new ProxyHarness(stream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains(SECRET), "the input_json_delta token is reversed");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void heldToolUsePartialFlushesAsInputJsonDeltaNotText(@TempDir Path logs) {
        FakeUpstream stream = new FakeUpstream(CannedReply.sse(JSON_HELD_PARTIAL_STREAM));
        ProxyHarness harness = new ProxyHarness(stream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertFalse(
                response.body().contains("text_delta"),
                "a held tool-use partial must flush as input_json_delta, never as text_delta"
            );
            assertTrue(response.body().contains("input_json_delta"), "the synthetic flush keeps the tool-use type");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void splitTokenAcrossTwoDeltasIsReassembled(@TempDir Path logs) {
        FakeUpstream stream = new FakeUpstream(CannedReply.sse(SPLIT_STREAM));
        ProxyHarness harness = new ProxyHarness(stream, logs);
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
    void splitTokenIsReassembledOnACrlfFramedStream(@TempDir Path logs) {
        FakeUpstream stream = new FakeUpstream(CannedReply.sse(CRLF_SPLIT_STREAM));
        ProxyHarness harness = new ProxyHarness(stream, logs);
        try {
            HttpResponse<String> response = drive(harness);
            assertTrue(response.body().contains(SECRET), "events framed with \\r\\n\\r\\n still reassemble");
            assertFalse(response.body().contains("<<PII:AWS_KEY"), "no token remains on a CRLF stream");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void streamedResponseLeavesARawWireDump(@TempDir Path logs) {
        FakeUpstream stream = new FakeUpstream(CannedReply.sse(SPLIT_STREAM));
        ProxyHarness harness = new ProxyHarness(stream, logs);
        try {
            drive(harness);
            assertTrue(hasRawResponseDump(logs.resolve("wire")), "a streamed response records its raw wire dump");
        } finally {
            harness.stop();
        }
    }

    @SneakyThrows
    private static boolean hasRawResponseDump(Path wireDirectory) {
        try (Stream<Path> entries = Files.list(wireDirectory)) {
            return entries.anyMatch(path -> path.getFileName().toString().endsWith("-raw-response.txt"));
        }
    }
}
