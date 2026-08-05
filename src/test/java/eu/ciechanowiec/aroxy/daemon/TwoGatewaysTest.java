package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.client.ClaudeCode;
import eu.ciechanowiec.aroxy.client.ClientRegistry;
import eu.ciechanowiec.aroxy.client.CodexCli;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves one daemon serves several agents at once: a listener per registered client, each reaching its
 * own upstream, all sharing one vault and one rule set.
 *
 * <p>Each client is pointed at its own in-JVM stand-in, which is what makes "the right listener
 * reached the right API" observable rather than assumed. The headline assertion is not that two ports
 * are bound but that the same secret carries the <em>same token</em> on both, since that is what a
 * shared vault means in practice: a value obfuscated in one agent's session is recognisable in the
 * other's.
 */
@Justification(
    "this socket-level integration test composes the daemon, every client profile, and HTTP fixtures"
)
@SuppressWarnings(
    "ClassWithTooManyDependencies"
)
class TwoGatewaysTest {

    private static final int OK = 200;
    private static final Pattern TOKEN = Pattern.compile("<<PII:AWS_KEY:\\d+>>");
    private static final String CODEX_BASE_PATH = "/backend-api/codex";
    private static final String CLAUDE_NAME = ClaudeCode.profile().displayName();
    private static final String CODEX_NAME = CodexCli.profile().displayName();

    @TempDir
    private Path base;
    private UpstreamStub claudeCodeApi;
    private UpstreamStub codexApi;
    private Daemon daemon;
    private Home home;

    @BeforeEach
    @SneakyThrows
    void startDaemon() {
        this.claudeCodeApi = new UpstreamStub();
        this.codexApi = new UpstreamStub();
        this.home = DaemonHarness.prepare(this.base);
        this.daemon = new Daemon(
            this.home, DaemonSeam.eachAt(
                Map.of(
                    ClaudeCode.ID, this.claudeCodeApi.uri(),
                    CodexCli.ID, URI.create(this.codexApi.uri() + CODEX_BASE_PATH)
                )
            )
        );
        this.daemon.run();
    }

    @AfterEach
    void stopDaemon() {
        this.daemon.stop();
        this.claudeCodeApi.stop();
        this.codexApi.stop();
    }

    @Test
    void bindsOneListenerPerRegisteredClient() {
        assertEquals(
            ClientRegistry.profiles().size(),
            this.snapshot().gateways().size(),
            "one listener per registered client"
        );
        assertNotEquals(
            listenOf(CLAUDE_NAME), listenOf(CODEX_NAME),
            "each client gets its own socket, so neither has to be routed by guessing a path"
        );
    }

    @Test
    void eachListenerReachesOnlyItsOwnUpstream() {
        this.post(CLAUDE_NAME, "/v1/messages", DaemonHarness.SECRET);
        assertTrue(this.claudeCodeApi.lastBody().contains("<<PII:AWS_KEY:"), "the Claude API stub was reached");
        assertEquals("", this.codexApi.lastBody(), "and the ChatGPT stub was not");
        this.post(CODEX_NAME, "/responses", DaemonHarness.SECRET);
        assertTrue(this.codexApi.lastBody().contains("<<PII:AWS_KEY:"), "the ChatGPT stub was reached");
    }

    @Test
    void theSameSecretCarriesTheSameTokenOnBothListeners() {
        this.post(CLAUDE_NAME, "/v1/messages", DaemonHarness.SECRET);
        String viaClaudeCode = tokenIn(this.claudeCodeApi.lastBody());
        this.post(CODEX_NAME, "/responses", DaemonHarness.SECRET);
        String viaCodex = tokenIn(this.codexApi.lastBody());
        assertEquals(
            viaClaudeCode, viaCodex,
            "one vault and one rule set serve every agent, so a value obfuscated in one session "
                + "is the same token in the other"
        );
    }

    @Test
    void neitherListenerLetsTheSecretLeaveTheMachine() {
        this.post(CLAUDE_NAME, "/v1/messages", DaemonHarness.SECRET);
        this.post(CODEX_NAME, "/responses", DaemonHarness.SECRET);
        assertFalse(this.claudeCodeApi.lastBody().contains(DaemonHarness.SECRET), "not past the Claude listener");
        assertFalse(this.codexApi.lastBody().contains(DaemonHarness.SECRET), "not past the Codex listener");
    }

    @Test
    void theUpstreamBasePathIsPreservedWhenForwarding() {
        this.post(CODEX_NAME, "/responses", "hello");
        assertEquals(
            CODEX_BASE_PATH + "/responses", this.codexApi.lastPath(),
            "the ChatGPT base carries a path, so the request path is appended to it rather than replacing it"
        );
    }

    @Test
    void eachListenerOnlyObfuscatesItsOwnPaths() {
        this.post(CODEX_NAME, "/v1/messages", DaemonHarness.SECRET);
        assertTrue(
            this.codexApi.lastBody().contains(DaemonHarness.SECRET),
            "a Claude Code path is out of the Codex listener's scope, so it is forwarded untouched"
        );
    }

    private static String tokenIn(CharSequence body) {
        Matcher matcher = TOKEN.matcher(body);
        assertTrue(matcher.find(), "a token is present in " + body);
        return matcher.group();
    }

    private String listenOf(String displayName) {
        return this.snapshot().gateways().stream()
            .filter(gateway -> gateway.client().equals(displayName))
            .findFirst()
            .orElseThrow()
            .listen();
    }

    private RuntimeSnapshot snapshot() {
        return RuntimeSnapshot.read(this.home.runtimeFile()).orElseThrow();
    }

    @SneakyThrows
    private void post(String displayName, String path, String secret) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + this.listenOf(displayName) + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"q\":\"" + secret + "\"}"))
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(OK, response.statusCode(), response.body());
        }
    }
}
