package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Deobfuscates OpenAI Chat Completions SSE with one carry buffer per choice, reasoning field, and
 * parallel tool call. Keeping those lanes separate prevents a partial token in one tool's arguments
 * from being joined to bytes belonging to another tool.
 */
final class ChatCompletionTransformer {

    private static final String DELIMITER = "\n\n";
    private static final String DONE = "[DONE]";
    private final OutputStream output;
    private final Obfuscator obfuscator;
    private final ByteArrayOutputStream pending;
    private final Map<ChatLane, StreamDeobfuscator> lanes;
    private final Map<ChatLane, String> templates;

    ChatCompletionTransformer(OutputStream output, Obfuscator obfuscator) {
        this.output = output;
        this.obfuscator = obfuscator;
        this.pending = new ByteArrayOutputStream();
        this.lanes = new LinkedHashMap<>();
        this.templates = new LinkedHashMap<>();
    }

    void push(byte[] chunk, int length) throws IOException {
        this.pending.write(chunk, 0, length);
        byte[] buffer = this.pending.toByteArray();
        int start = this.drain(buffer);
        this.pending.reset();
        this.pending.write(buffer, start, buffer.length - start);
        this.output.flush();
    }

    void finish() throws IOException {
        byte[] remainder = this.pending.toByteArray();
        if (remainder.length > 0) {
            this.write(this.transform(new String(remainder, StandardCharsets.UTF_8)));
        }
        this.write(this.flush());
        this.output.flush();
        this.pending.reset();
    }

    int reversedCount() {
        return this.lanes.values().stream().mapToInt(StreamDeobfuscator::reversedCount).sum();
    }

    private int drain(byte[] buffer) throws IOException {
        int start = 0;
        Optional<Delimiter> delimiter = DoubleNewline.find(buffer, start);
        while (delimiter.isPresent()) {
            Delimiter found = delimiter.orElseThrow();
            String block = block(buffer, start, found);
            this.write(this.transform(block) + DELIMITER);
            start = found.next();
            delimiter = DoubleNewline.find(buffer, start);
        }
        return start;
    }

    private static String block(byte[] buffer, int start, Delimiter delimiter) {
        return new String(buffer, start, delimiter.contentEnd() - start, StandardCharsets.UTF_8);
    }

    private String transform(String block) {
        Optional<JsonBlock> parsed = JsonBlock.parse(block);
        if (parsed.isEmpty()) {
            return block.contains(DONE) ? this.flush() + block : block;
        }
        JsonBlock event = parsed.orElseThrow();
        this.transformChoices(event.json(), block);
        return event.render();
    }

    private void transformChoices(JSONObject root, String block) {
        Optional.ofNullable(root.optJSONArray("choices"))
            .ifPresent(choices -> this.transformChoices(root, choices, block));
    }

    private void transformChoices(JSONObject root, JSONArray choices, String block) {
        for (int choice = 0; choice < choices.length(); choice++) {
            this.transformChoice(root, choices.getJSONObject(choice), choice, block);
        }
    }

    private void transformChoice(JSONObject root, JSONObject item, int fallback, String block) {
        int index = item.optInt("index", fallback);
        Optional.ofNullable(item.optJSONObject("delta"))
            .ifPresent(delta -> this.transformDelta(new DeltaContext(root, delta, index, block)));
    }

    private void transformDelta(DeltaContext context) {
        this.direct(context, "content");
        this.direct(context, "reasoning_content");
        this.direct(context, "reasoning");
        Optional.ofNullable(context.delta().optJSONArray("tool_calls"))
            .ifPresent(tools -> this.transformTools(context, tools));
    }

    private void transformTools(DeltaContext context, JSONArray tools) {
        for (int tool = 0; tool < tools.length(); tool++) {
            this.transformTool(context, tools.getJSONObject(tool), tool);
        }
    }

    private void transformTool(DeltaContext context, JSONObject toolCall, int fallback) {
        this.tool(context, toolCall, toolCall.optInt("index", fallback));
    }

    private void direct(DeltaContext context, String field) {
        if (context.delta().opt(field) instanceof String value) {
            this.accept(
                context.root(), ChatLane.direct(context.choice(), field), value, context.block()
            );
        }
    }

    private void tool(DeltaContext context, JSONObject toolCall, int tool) {
        Optional.ofNullable(toolCall.optJSONObject("function"))
            .map(function -> function.opt("arguments"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .ifPresent(
                value -> this.accept(
                    context.root(), ChatLane.tool(context.choice(), tool), value, context.block()
                )
            );
    }

    private void accept(JSONObject root, ChatLane lane, String value, String block) {
        StreamDeobfuscator deobfuscator = this.lanes.computeIfAbsent(
            lane, _ -> new StreamDeobfuscator(this.obfuscator)
        );
        this.templates.put(lane, block);
        lane.put(root, deobfuscator.accept(value));
    }

    private String flush() {
        StringBuilder replayed = new StringBuilder();
        this.lanes.forEach((lane, deobfuscator) -> appendReplay(replayed, lane, deobfuscator.flush()));
        return replayed.toString();
    }

    private void appendReplay(StringBuilder replayed, ChatLane lane, String held) {
        if (held.isEmpty()) {
            return;
        }
        String template = Optional.ofNullable(this.templates.get(lane)).orElseThrow();
        JsonBlock event = JsonBlock.parse(template).orElseThrow();
        ChatLane.clear(event.json());
        lane.put(event.json(), held);
        replayed.append(event.render()).append(DELIMITER);
    }

    private void write(String text) throws IOException {
        this.output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private record JsonBlock(String prefix, JSONObject json, String suffix) {

        @Justification(
            "the parser belongs to its private value type and is intentionally called by the enclosing transformer"
        )
        @SuppressWarnings(
            "StaticMethodOnlyUsedInOneClass"
        )
        static Optional<JsonBlock> parse(String block) {
            int start = block.indexOf('{');
            int end = block.lastIndexOf('}');
            Optional<JsonBlock> parsed = Optional.empty();
            if (start >= 0 && end >= start) {
                parsed = parse(block, start, end);
            }
            return parsed;
        }

        private static Optional<JsonBlock> parse(String block, int start, int end) {
            Optional<JsonBlock> parsed = Optional.empty();
            try {
                parsed = Optional.of(
                    new JsonBlock(
                        block.substring(0, start), new JSONObject(block.substring(start, end + 1)),
                        block.substring(end + 1)
                    )
                );
            } catch (JSONException _) {
                // A non-JSON SSE block passes through unchanged.
            }
            return parsed;
        }

        String render() {
            return this.prefix + this.json + this.suffix;
        }
    }

    private record DeltaContext(JSONObject root, JSONObject delta, int choice, String block) {
    }
}
