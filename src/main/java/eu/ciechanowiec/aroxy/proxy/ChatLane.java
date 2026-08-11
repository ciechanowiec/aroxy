package eu.ciechanowiec.aroxy.proxy;

import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One independently streamed Chat Completions value.
 *
 * @param choice the position in the choices array
 * @param field  the direct delta field, empty for a tool call
 * @param tool   the position in the tool-calls array, negative for a direct field
 */
record ChatLane(int choice, String field, int tool) {

    private static final int NO_TOOL = -1;
    private static final String INDEX = "index";

    static ChatLane direct(int choice, String field) {
        return new ChatLane(choice, field, NO_TOOL);
    }

    static ChatLane tool(int choice, int tool) {
        return new ChatLane(choice, "arguments", tool);
    }

    void put(JSONObject root, String value) {
        JSONObject selected = indexed(root.getJSONArray("choices"), this.choice);
        JSONObject delta = selected.getJSONObject("delta");
        if (this.tool == NO_TOOL) {
            delta.put(this.field, value);
        } else {
            indexed(delta.getJSONArray("tool_calls"), this.tool)
                .getJSONObject("function").put("arguments", value);
        }
    }

    private static JSONObject indexed(JSONArray values, int index) {
        for (int position = 0; position < values.length(); position++) {
            JSONObject candidate = values.getJSONObject(position);
            if (candidate.optInt(INDEX, position) == index) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No streamed value has index " + index);
    }

    static void clear(JSONObject root) {
        Optional.ofNullable(root.optJSONArray("choices")).ifPresent(ChatLane::clearChoices);
    }

    private static void clearChoices(JSONArray choices) {
        for (int choice = 0; choice < choices.length(); choice++) {
            Optional.ofNullable(choices.optJSONObject(choice)).ifPresent(ChatLane::clearDelta);
        }
    }

    private static void clearDelta(JSONObject choice) {
        Optional.ofNullable(choice.optJSONObject("delta")).ifPresent(ChatLane::clearFields);
    }

    private static void clearFields(JSONObject delta) {
        clearDirect(delta, "content");
        clearDirect(delta, "reasoning_content");
        clearDirect(delta, "reasoning");
        Optional.ofNullable(delta.optJSONArray("tool_calls")).ifPresent(ChatLane::clearTools);
    }

    private static void clearTools(JSONArray tools) {
        for (int tool = 0; tool < tools.length(); tool++) {
            Optional.ofNullable(tools.optJSONObject(tool)).ifPresent(ChatLane::clearTool);
        }
    }

    private static void clearDirect(JSONObject delta, String field) {
        if (delta.opt(field) instanceof String) {
            delta.put(field, "");
        }
    }

    private static void clearTool(JSONObject tool) {
        Optional.ofNullable(tool.optJSONObject("function"))
            .filter(function -> function.opt("arguments") instanceof String)
            .ifPresent(function -> function.put("arguments", ""));
    }
}
