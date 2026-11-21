package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.aroxy.proxy.TrafficTotals;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StatusViewTest {

    private static final long UPTIME_MILLIS = 1000L;
    private static final GatewayAddress CLAUDE_CODE = new GatewayAddress(
        "Claude Code", "127.0.0.1:8788", "https://api.example"
    );
    private static final GatewayAddress CODEX = new GatewayAddress(
        "Codex CLI", "127.0.0.1:8789", "https://codex.example"
    );

    @Test
    void rendersRuntimeFieldsHumanReadably() {
        StatusReport report = new StatusReport(
            List.of(CLAUDE_CODE), 3, UPTIME_MILLIS,
            List.of(new AttachmentInfo("/etc/a.conf", AttachmentKind.PERSISTENT, 1L)), Optional.empty()
        );
        String text = StatusView.render(report);
        assertTrue(text.contains("Claude Code  http://127.0.0.1:8788  →  https://api.example"), text);
        assertTrue(text.contains("Rules        3"), text);
        assertTrue(text.contains("Uptime       0h 0m 1s"), text);
    }

    @Test
    void namesEveryListenerSoEachAgentKnowsWhereToPoint() {
        StatusReport report = new StatusReport(List.of(CLAUDE_CODE, CODEX), 1, 1L, List.of(), Optional.empty());
        String text = StatusView.render(report);
        assertTrue(text.contains("Claude Code  http://127.0.0.1:8788  →  https://api.example"), text);
        assertTrue(
            text.contains("Codex CLI    http://127.0.0.1:8789  →  https://codex.example"),
            "labels are padded to a common width, so the addresses line up: " + text
        );
    }

    @Test
    void rendersContributingFileWithKindAndLoadTime() {
        StatusReport report = new StatusReport(
            List.of(CLAUDE_CODE), 1, 1L,
            List.of(new AttachmentInfo("/etc/a.conf", AttachmentKind.PERSISTENT, 1L)), Optional.empty()
        );
        String text = StatusView.render(report);
        assertTrue(text.contains("persistent"), text);
        assertTrue(text.contains("/etc/a.conf"), text);
    }

    @Test
    void rendersTrafficBetweenTheRulesAndTheListeners() {
        TrafficReport traffic = TrafficReport.at(new TrafficTotals(2L, 1L, 1L, 0L, 0L, 0L), 1L);
        String text = StatusView.render(
            new StatusReport(List.of(CLAUDE_CODE), 1, 1L, List.of(), Optional.of(traffic))
        );
        assertTrue(text.contains("  Requests     2 (1 redacted)\n"), text);
        assertTrue(text.contains("  Last request never\n"), text);
        assertTrue(text.indexOf("Rules") < text.indexOf("Requests"), text);
        assertTrue(text.indexOf("Last request") < text.indexOf("Listeners"), text);
    }

    @Test
    void omitsTrafficWhenNoneWasPublished() {
        String text = StatusView.render(new StatusReport(List.of(CLAUDE_CODE), 1, 1L, List.of(), Optional.empty()));
        assertFalse(text.contains("Requests"), text);
    }

    @Test
    void rendersAPlaceholderWhenNothingIsBound() {
        String text = StatusView.render(new StatusReport(List.of(), 0, 1L, List.of(), Optional.empty()));
        assertTrue(text.contains("(no listeners)"), text);
    }
}
