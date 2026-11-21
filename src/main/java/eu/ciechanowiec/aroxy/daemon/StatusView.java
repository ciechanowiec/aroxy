package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.airness.Justification;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Renders a {@link StatusReport} as a compact, human-readable text block for {@code aroxy status}:
 * whether the daemon runs, one line per bound listener naming the address to point that agent at and
 * the upstream it reaches, the active rule count, the uptime, the traffic carried since the start, and
 * the contributing configuration files with their kind and load time. The CLI builds the report from
 * the daemon's published {@link RuntimeSnapshot} and its traffic counters, and prints this body,
 * mirroring how {@code config} and {@code config list} are rendered.
 *
 * <p>The listeners are rendered as an indented list rather than as one line per named client, so this
 * view does not change when a client is registered or dropped.
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
        List<String> lines = new ArrayList<>();
        lines.add(INDENT + "Uptime       " + uptime(report.uptimeMillis()));
        lines.add(INDENT + "Rules        " + report.ruleCount());
        report.traffic().stream()
            .map(TrafficView::lines)
            .flatMap(List::stream)
            .forEach(line -> lines.add(INDENT + line));
        lines.add(INDENT + "Listeners");
        lines.add(indent(GatewayListing.render(report.gateways())));
        lines.add(INDENT + "Configuration files");
        lines.add(indent(AttachmentListing.render(report.attachments())));
        return lines;
    }

    @Justification(
        "intended format: no space between each number and its unit (12h 5m 3s)"
    )
    @SuppressWarnings(
        "StringConcatenationMissingWhitespace"
    )
    private static String uptime(long millis) {
        Duration elapsed = Duration.ofMillis(millis);
        return elapsed.toHours() + "h " + elapsed.toMinutesPart() + "m " + elapsed.toSecondsPart() + "s";
    }

    private static String indent(String block) {
        String prefix = INDENT.repeat(2);
        return block.lines().map(line -> prefix + line).collect(Collectors.joining("\n"));
    }
}
