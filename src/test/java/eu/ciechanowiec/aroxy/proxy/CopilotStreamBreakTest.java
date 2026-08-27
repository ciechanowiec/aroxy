package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A Copilot stream that dies in flight must still leave evidence. The success path dumps its capture
 * only once the pump loop ends, so without a dump on failure the one exchange worth investigating is
 * the one that records nothing.
 */
class CopilotStreamBreakTest {

    private static final String CARRIED = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"carried\"}\n\n";

    private final Path base;
    private final FakeUpstream upstream;
    private final ProxyHarness proxy;

    @SneakyThrows
    CopilotStreamBreakTest(@TempDir Path base) {
        this.base = base;
        this.upstream = new FakeUpstream(CannedReply.sse(CARRIED), ReplyEnding.TRUNCATED);
        this.proxy = new ProxyHarness(this.upstream, base, CopilotFixtures.profile());
    }

    @AfterEach
    void stop() {
        this.proxy.stop();
    }

    @Test
    @SneakyThrows
    void dumpsWhatABrokenStreamCarriedBeforeItDied() {
        this.attempt();
        List<Path> dumps = CopilotFixtures.wireFiles(this.base);
        assertEquals(1, dumps.size(), "a stream that broke still files a wire dump");
        assertTrue(
            Files.readString(dumps.getFirst()).contains("carried"),
            "the dump holds what the stream carried before it broke"
        );
    }

    /**
     * Drives one exchange whose upstream dies mid-stream. The client outcome is deliberately not
     * asserted: a truncated stream may surface as a short body or as an exception depending on how
     * far the connection got, and the evidence this test is about is what the proxy filed.
     */
    private void attempt() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://localhost:" + this.upstream.uri().getPort() + "/responses"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"input\":\"" + ProxyHarness.SECRET + "\"}"))
            .build();
        try (HttpClient http = CopilotFixtures.client(this.proxy.base(), this.base)) {
            http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IllegalStateException | IOException | InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
