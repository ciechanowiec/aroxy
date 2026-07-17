package eu.ciechanowiec.aroxy.daemon;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Renders a {@link StatusReport} as a compact, human-readable text block for {@code aroxy status}:
 * whether the daemon runs, its listen address, the upstream, the active rule count, the uptime, and
 * the contributing configuration files with their kind and load time. The CLI builds the report from
 * the daemon's published {@link RuntimeSnapshot} and prints this body, mirroring how {@code config}
 * and {@code config list} are rendered.
 */
@UtilityClass
public final class StatusView {

    private static final String INDENT = "  ";

    /**
     * Renders the {@code status} view for a running daemon.
     *
     * @param report the status report to render
     * @return the rendered, newline-terminated status text
     */
    public static String render(StatusReport report) {
        return String.join("\n", lines(report)) + "\n";
    }

    private static List<String> lines(StatusReport report) {
        return List.of(
            "running  = true",
            "listen   = " + report.listenAddress(),
            "upstream = " + report.upstream(),
            "rules    = " + report.ruleCount(),
            "uptime   = " + uptime(report.uptimeMillis()),
            "files    =",
            indent(AttachmentListing.render(report.attachments()))
        );
    }

    // intended format: no space between each number and its unit (12h 5m 3s)
    @SuppressWarnings(
        "StringConcatenationMissingWhitespace"
    )
    private static String uptime(long millis) {
        Duration elapsed = Duration.ofMillis(millis);
        return elapsed.toHours() + "h " + elapsed.toMinutesPart() + "m " + elapsed.toSecondsPart() + "s";
    }

    private static String indent(String block) {
        return block.lines().map(line -> INDENT + line).collect(Collectors.joining("\n"));
    }
}
