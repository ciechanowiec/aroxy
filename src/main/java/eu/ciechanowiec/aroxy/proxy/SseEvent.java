package eu.ciechanowiec.aroxy.proxy;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One Server-Sent-Events block (the text between two blank-line delimiters), with the predicates the
 * transformer needs: whether it is a {@code content_block_delta} carrying a {@code text_delta} or
 * {@code input_json_delta} (and so its value must be reversed), whether it is a terminal event that
 * should trigger a flush of any held partial token, and the content-block {@code index} it refers to.
 *
 * <p>Only {@code content_block_stop} and {@code message_stop} are treated as terminal. A
 * {@code message_delta} (which carries {@code stop_reason}/{@code usage} and arrives after the block
 * has already closed) is deliberately not terminal: flushing a held partial there would inject a
 * synthetic {@code text_delta} under an index whose {@code content_block_stop} has already passed.
 */
record SseEvent(String block) {

    private static final Pattern INDEX = Pattern.compile("\"index\":(\\d+)");

    boolean isTextDelta() {
        return this.block.contains("\"content_block_delta\"")
            && (this.block.contains("\"text_delta\"") || this.block.contains("\"input_json_delta\""));
    }

    boolean isTerminal() {
        return this.block.contains("\"content_block_stop\"")
            || this.block.contains("\"message_stop\"");
    }

    Optional<Integer> index() {
        Matcher matcher = INDEX.matcher(this.block);
        return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }
}
