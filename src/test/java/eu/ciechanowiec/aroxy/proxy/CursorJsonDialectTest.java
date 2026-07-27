package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.client.CursorCli;
import eu.ciechanowiec.governance.Justification;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cursor's Connect-JSON dialect over real sockets. It is accepted even though Cursor negotiates
 * protobuf, so it carries the same obligations: every envelope reaches the agent, a token split across
 * envelopes reverses, a partial still held when the stream stops is released, a compressed envelope is
 * refused rather than handed on unread, and the turn is audited once it ends.
 */
class CursorJsonDialectTest {

    private static final String TOKEN = "<<PII:AWS_KEY:1>>";
    private static final String RUN = "/agent.v1.AgentService/RunSSE";
    private static final String CONNECT_JSON = "application/connect+json";

    @Test
    @SneakyThrows
    void obfuscatesThePromptAndRestoresTheConnectResponse(@TempDir Path logs) {
        String responseJson = """
            {"interactionUpdate":{"textDelta":{"text":"%s"}}}
            """.formatted(TOKEN).trim();
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connect(
                CursorWire.text(CursorWire.frame(responseJson.getBytes(StandardCharsets.UTF_8)))
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            byte[] requestBody = CursorWire.frame(
                CursorWire.requestJson().getBytes(StandardCharsets.UTF_8)
            );
            HttpRequest request = HttpRequest.newBuilder()
                .uri(harness.base().resolve(RUN))
                .header("Content-Type", "application/connect+json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
            byte[] response = sendBytes(request);
            assertFalse(
                upstream.lastBody().contains(ProxyHarness.SECRET), "the secret never reaches Cursor"
            );
            assertTrue(upstream.lastBody().contains(TOKEN), "Cursor receives the stable token");
            assertTrue(
                CursorWire.payload(response).contains(ProxyHarness.SECRET),
                "the CLI receives the original value"
            );
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void restoresATokenSplitAcrossCursorDeltaFrames(@TempDir Path logs) {
        String first = "{\"interactionUpdate\":{\"textDelta\":{\"text\":\"<<PII:AWS_\"}}}";
        String second = "{\"interactionUpdate\":{\"textDelta\":{\"text\":\"KEY:1>>\"}}}";
        String terminal = "{\"interactionUpdate\":{\"turnEnded\":{}}}";
        String responseBody = CursorWire.text(CursorWire.frame(first.getBytes(StandardCharsets.UTF_8)))
            + CursorWire.text(CursorWire.frame(second.getBytes(StandardCharsets.UTF_8)))
            + CursorWire.text(CursorWire.frame(terminal.getBytes(StandardCharsets.UTF_8)));
        FakeUpstream upstream = new FakeUpstream(CannedReply.connect(responseBody));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            byte[] response = send(
                harness, CursorWire.frame(CursorWire.requestJson().getBytes(StandardCharsets.UTF_8))
            );
            String wire = new String(response, StandardCharsets.UTF_8);
            assertTrue(wire.contains(ProxyHarness.SECRET), "the split token is restored");
            assertFalse(wire.contains(TOKEN), "no token remains in the Cursor stream");
        } finally {
            harness.stop();
        }
    }

    @Test
    @SneakyThrows
    void flushesAnIncompleteTokenFromATerminalCursorDelta(@TempDir Path logs) {
        String partial = "{\"interactionUpdate\":{\"textDelta\":{\"text\":\"<<PII:AWS_\"}}}";
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connect(
                CursorWire.text(CursorWire.framed(ConnectJsonFrame.END_STREAM, partial))
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            String response = new String(
                send(
                    harness,
                    CursorWire.frame(CursorWire.requestJson().getBytes(StandardCharsets.UTF_8))
                ),
                StandardCharsets.UTF_8
            );
            assertTrue(response.contains("<<PII:AWS_"), response);
        } finally {
            harness.stop();
        }
    }

    // The JSON dialect is accepted even though Cursor negotiates protobuf, so it needs the same
    // guarantees: every envelope reaches the agent, a compressed one is refused rather than passed on
    // as opaque bytes, and the turn is audited once it ends.
    @Test
    @SneakyThrows
    void writesEveryEnvelopeOfAJsonStreamAndAuditsTheTurn(@TempDir Path logs) {
        String first = "{\"interactionUpdate\":{\"textDelta\":{\"text\":\"one \"}}}";
        String second = "{\"interactionUpdate\":{\"textDelta\":{\"text\":\"" + TOKEN + "\"}}}";
        String ended = "{\"interactionUpdate\":{\"turnEnded\":{}}}";
        String body = CursorWire.text(CursorWire.frame(first.getBytes(StandardCharsets.UTF_8)))
            + CursorWire.text(CursorWire.frame(second.getBytes(StandardCharsets.UTF_8)))
            + CursorWire.text(CursorWire.frame(ended.getBytes(StandardCharsets.UTF_8)));
        FakeUpstream upstream = new FakeUpstream(CannedReply.connect(body));
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            String wire = CursorWire.text(
                send(harness, CursorWire.frame(CursorWire.requestJson().getBytes(StandardCharsets.UTF_8)))
            );
            assertTrue(wire.contains("one "), "the first envelope reaches the agent: " + wire);
            assertTrue(wire.contains(ProxyHarness.SECRET), "and so does the restored second one");
            assertTrue(wire.contains("turnEnded"), "and so does the one that ends the turn");
        } finally {
            harness.stop();
        }
        String audit = auditOf(logs);
        assertTrue(audit.contains("RESPONSE"), "the turn is recorded once it ends: " + audit);
    }

    // A stream can stop without ever saying the turn ended. Whatever is still held has to be released
    // and the turn still recorded, or a reply that simply stops loses its tail and its audit entry.
    @Test
    @SneakyThrows
    void aJsonStreamThatEndsWithoutAMarkerStillFlushesAndAudits(@TempDir Path logs) {
        String partial = "{\"interactionUpdate\":{\"textDelta\":{\"text\":\"tail <<PII:AWS_\"}}}";
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connect(
                CursorWire.text(CursorWire.frame(partial.getBytes(StandardCharsets.UTF_8)))
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            String wire = CursorWire.text(
                send(harness, CursorWire.frame(CursorWire.requestJson().getBytes(StandardCharsets.UTF_8)))
            );
            assertTrue(wire.contains("tail "), wire);
            assertTrue(
                wire.contains("<<PII:AWS_"),
                "the held remainder is released even with no completion marker: " + wire
            );
        } finally {
            harness.stop();
        }
        String audit = auditOf(logs);
        assertTrue(audit.contains("RESPONSE"), "and the turn is still recorded: " + audit);
        String stamp = audit.lines().filter(line -> line.contains("REQUEST"))
            .map(line -> line.substring(line.indexOf('[') + 1, line.indexOf(']')))
            .findFirst().orElse("");
        assertTrue(
            dumpText(logs.resolve("wire").resolve(WireBody.BINARY.responseFile(stamp))).contains("tail "),
            "and the envelopes it carried are in the dump"
        );
    }

    @SneakyThrows
    @Justification(
        "Files.readString rejects a framed dump, which is not valid UTF-8, and the lenient decode is the point"
    )
    @SuppressWarnings(
        "ReadWriteStringCanBeUsed"
    )
    private static String dumpText(Path dump) {
        return new String(Files.readAllBytes(dump), StandardCharsets.UTF_8);
    }

    // A compressed envelope cannot be transformed, so it is refused. Forwarding it would put bytes the
    // proxy never inspected in front of the agent while the proxy reported itself healthy.
    @Test
    @SneakyThrows
    void refusesACompressedJsonResponseEnvelope(@TempDir Path logs) {
        FakeUpstream upstream = new FakeUpstream(
            CannedReply.connect(
                CursorWire.text(CursorWire.framed(1, "{\"interactionUpdate\":{}}"))
            )
        );
        ProxyHarness harness = new ProxyHarness(upstream, logs, CursorCli.profile());
        try {
            String wire = CursorWire.text(
                send(harness, CursorWire.frame(CursorWire.requestJson().getBytes(StandardCharsets.UTF_8)))
            );
            assertFalse(
                wire.contains("interactionUpdate"),
                "an envelope aroxy cannot read is not handed on: " + wire
            );
        } finally {
            harness.stop();
        }
    }

    @SneakyThrows
    private static byte[] send(ProxyHarness harness, byte[] body) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(harness.base().resolve(RUN))
            .header("Content-Type", CONNECT_JSON)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        }
    }

    @SneakyThrows
    private static byte[] sendBytes(HttpRequest request) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        }
    }

    @SneakyThrows
    private static String auditOf(Path logs) {
        return Files.readString(logs.resolve("audit.log"));
    }
}
