package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.CursorCli;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every transformed request leaves a trail: a request line naming what was replaced, a response line
 * naming how much was restored, and the two wire dumps those lines point at. That trail is what the
 * documentation tells a user to read as proof, so a path that quietly stops writing it is a defect
 * even though every response still looks correct.
 *
 * <p>The stamp is checked as well as the lines, because it is what joins an audit entry to its dumps.
 * A trail whose parts cannot be matched to each other proves nothing.
 */
class AuditTrailTest {

    private static final String RUN = "/agent.v1.AgentService/Run";
    private static final String APPEND = "/aiserver.v1.BidiService/BidiAppend";
    private static final String CONNECT_PROTO = "application/connect+proto";
    private static final String PROTO = "application/proto";
    private static final String REQUEST = "REQUEST";
    private static final String RESPONSE = "RESPONSE";
    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    /*
     * An SSE event ends with a blank line. Written as a field rather than inside a test, because the
     * empty-line rule is scoped to method bodies while the formatter insists on a text block and Qodana
     * insists the newline in one be real, and a field satisfies all three.
     */
    private static final String TOKEN_EVENT = """
        data: {"type":"content_block_delta","delta":\
        {"type":"text_delta","text":"<<PII:AWS_KEY:1>>"}}

        """;
    private static final String PARTIAL_EVENT = """
        data: {"type":"content_block_delta","delta":\
        {"type":"text_delta","text":"tail <<PII:AWS_"}}

        """;

    @Test
    @SneakyThrows
    void theBufferedPathRecordsItsRequestResponseAndDumps(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.json("{\"answer\":\"ok\"}"));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            HttpResponse<String> ignored = Http.post(
                harness.base(), Http.MESSAGES, "{\"q\":\"" + ProxyHarness.SECRET + "\"}"
            );
            assertEquals(Http.OK, ignored.statusCode());
        } finally {
            harness.stop();
        }
        assertTrail(logs, Http.MESSAGES, WireBody.TEXT);
    }

    @Test
    @SneakyThrows
    void theStreamedPathRecordsItsResponseLine(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(TOKEN_EVENT));
        ProxyHarness harness = new ProxyHarness(upstream, logs);
        try {
            Http.post(harness.base(), Http.MESSAGES, "{\"q\":\"" + ProxyHarness.SECRET + "\"}");
        } finally {
            harness.stop();
        }
        assertTrue(audit(logs).contains(RESPONSE), "a streamed reply is recorded too: " + audit(logs));
        assertTrue(audit(logs).contains("(stream)"), "and is recorded as streamed");
        String stamp = stampFrom(audit(logs));
        String dumped = Files.readString(
            logs.resolve("wire").resolve(stamp + "-raw-response.txt")
        );
        assertTrue(
            dumped.contains(TOKEN),
            "the streamed bytes are captured as they pass, not lost to the transformer: " + dumped
        );
    }

    // A stream can stop while the transformer still holds a partial token. Ending the transform is what
    // releases it, and without that the agent silently loses the tail of the reply.
    @Test
    @SneakyThrows
    void aStreamEndingMidTokenStillDeliversItsTail(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(CannedReply.sse(PARTIAL_EVENT));
        String body = reply(new ProxyHarness(upstream, logs));
        assertTrue(body.contains("tail "), body);
        assertTrue(
            body.contains("<<PII:AWS_"),
            "the held partial is released when the stream ends: " + body
        );
    }

    @Test
    @SneakyThrows
    void theCursorBufferedMethodRecordsItsTrail(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(
            new CannedReply(
                Http.OK, Map.of("Content-Type", PROTO),
                CursorWire.text(CursorWire.protobuf("acknowledged"))
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(harness.base().resolve(APPEND))
                .header("Content-Type", PROTO)
                .POST(
                    HttpRequest.BodyPublishers.ofByteArray(
                        CursorWire.protobuf(CursorWire.protobuf("Ask about " + ProxyHarness.KEYWORD))
                    )
                )
                .build();
            send(request);
        } finally {
            harness.stop();
        }
        assertTrail(logs, APPEND, WireBody.BINARY);
    }

    @Test
    @SneakyThrows
    void theCursorStreamRecordsItsTrail(@TempDir Path logs) {
        ProxyHarness harness = cursorStream(logs);
        try {
            sendStream(
                harness,
                CursorWire.concat(
                    CursorWire.frame(CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))),
                    CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
                )
            );
        } finally {
            harness.stop();
        }
        assertTrail(logs, RUN, WireBody.BINARY);
    }

    // A stream sends more than one envelope, and every one of them is both transformed and recorded.
    // Capturing only the first would leave the dump a partial record of what actually left the machine,
    // and matches found in a later envelope would go unaudited.
    @Test
    @SneakyThrows
    void everyEnvelopeOfAStreamIsRecorded(@TempDir Path logs) {
        ProxyHarness harness = cursorStream(logs);
        try {
            sendStream(
                harness,
                CursorWire.concat(
                    CursorWire.frame(CursorWire.protobuf(CursorWire.protobuf(ProxyHarness.SECRET))),
                    CursorWire.concat(
                        CursorWire.frame(
                            CursorWire.protobuf(CursorWire.protobuf("and " + ProxyHarness.KEYWORD))
                        ),
                        CursorWire.framed(ConnectJsonFrame.END_STREAM, new byte[0])
                    )
                )
            );
        } finally {
            harness.stop();
        }
        String audit = audit(logs);
        assertTrue(audit.contains("AWS_KEY"), "the first envelope's match is audited: " + audit);
        assertTrue(audit.contains("COMPANY"), "and so is the second envelope's: " + audit);
        String dump = text(
            logs.resolve("wire").resolve(WireBody.BINARY.requestFile(stampFrom(audit)))
        );
        assertTrue(dump.contains("<<PII:AWS_KEY:"), "the first envelope is in the dump");
        assertTrue(dump.contains("<<PII:COMPANY:"), "and so is the second: " + dump);
    }

    // The request line, the response line, and both dumps, all carrying the one stamp that joins them.
    // The dump names are asserted against the kind the client actually writes, so a client whose bytes
    // are framed binary is not silently accepted under a name promising JSON.
    @SneakyThrows
    private static void assertTrail(Path logs, CharSequence endpoint, WireBody kind) {
        String audit = audit(logs);
        assertTrue(hasLine(audit, REQUEST, endpoint), "a request line for " + endpoint + ": " + audit);
        assertTrue(hasLine(audit, RESPONSE, endpoint), "a response line for " + endpoint + ": " + audit);
        String stamp = stampFrom(audit);
        assertFalse(stamp.isBlank(), "the audit line carries a stamp: " + audit);
        List<String> dumps = dumpNames(logs);
        assertTrue(
            dumps.stream().anyMatch(name -> name.equals(kind.requestFile(stamp))),
            "the redacted request was dumped under the audited stamp " + stamp + ": " + dumps
        );
        assertTrue(
            dumps.stream().anyMatch(name -> name.equals(kind.responseFile(stamp))),
            "the raw response was dumped under the audited stamp " + stamp + ": " + dumps
        );
        assertDumpsCarryContent(logs, stamp, kind);
    }

    // A dump that exists but is empty proves nothing, so the bytes matter as much as the file. The
    // request dump must hold what was actually sent, which is the token rather than the secret. The
    // bytes are decoded leniently because a framed dump is not valid UTF-8 by construction, and what
    // is being looked for in it is an ASCII token that survives the decoding either way.
    @SneakyThrows
    private static void assertDumpsCarryContent(Path logs, String stamp, WireBody kind) {
        Path wire = logs.resolve("wire");
        String sent = text(wire.resolve(kind.requestFile(stamp)));
        String received = text(wire.resolve(kind.responseFile(stamp)));
        assertFalse(sent.isEmpty(), "the redacted request dump is not empty");
        assertFalse(received.isEmpty(), "the raw response dump is not empty");
        assertFalse(sent.contains(ProxyHarness.SECRET), "and the request dump never holds the secret");
    }

    @SneakyThrows
    @Justification(
        "Files.readString rejects a framed dump, which is not valid UTF-8, and the lenient decode is the point"
    )
    @SuppressWarnings(
        "ReadWriteStringCanBeUsed"
    )
    private static String text(Path dump) {
        return new String(Files.readAllBytes(dump), StandardCharsets.UTF_8);
    }

    private static boolean hasLine(String audit, CharSequence kind, CharSequence endpoint) {
        return audit.lines().anyMatch(line -> line.contains(kind) && line.contains(endpoint));
    }

    private static String stampFrom(String audit) {
        return audit.lines()
            .filter(line -> line.contains(REQUEST))
            .map(line -> line.substring(line.indexOf('[') + 1, line.indexOf(']')))
            .findFirst()
            .orElse("");
    }

    @SneakyThrows
    private static List<String> dumpNames(Path logs) {
        try (Stream<Path> files = Files.list(logs.resolve("wire"))) {
            return files.map(Path::getFileName).map(Path::toString).sorted().toList();
        }
    }

    @SneakyThrows
    private static String audit(Path logs) {
        return Files.readString(logs.resolve("audit.log"));
    }

    // Both streaming cases stage the same upstream and post the same envelope shape, differing only in
    // what their frames carry. Staging and posting are written once so that each case reads as the
    // frames it supplies and the trail it then asserts, rather than as the plumbing around them.
    @SneakyThrows
    private static ProxyHarness cursorStream(Path logs) {
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connectProtobuf(
                CursorWire.text(CursorWire.frame(CursorWire.textDelta("hello")))
            )
        );
        return new ProxyHarness(upstream, logs, CursorCli.profile());
    }

    private static void sendStream(ProxyHarness harness, byte... body) {
        send(
            HttpRequest.newBuilder()
                .uri(harness.base().resolve(RUN))
                .header("Content-Type", CONNECT_PROTO)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
        );
    }

    @SneakyThrows
    private static void send(HttpRequest request) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        }
    }

    @SneakyThrows
    private static String reply(ProxyHarness harness) {
        try {
            return Http.post(
                harness.base(), Http.MESSAGES, "{\"q\":\"" + ProxyHarness.SECRET + "\"}"
            ).body();
        } finally {
            harness.stop();
        }
    }
}
