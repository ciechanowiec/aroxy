package eu.ciechanowiec.aroxy.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Covers direct and tool-call selectors plus the sparse shapes accepted while clearing replay events.
 */
class ChatLaneTest {

    private static final String ARGUMENTS = "arguments";
    private static final String CONTENT = "content";

    @Test
    void writesDirectAndToolValues() {
        JSONObject root = root();
        ChatLane.direct(0, CONTENT).put(root, "text");
        ChatLane.tool(0, 0).put(root, ARGUMENTS);
        JSONObject delta = root.getJSONArray("choices").getJSONObject(0).getJSONObject("delta");
        assertEquals("text", delta.getString(CONTENT));
        assertEquals(
            ARGUMENTS,
            delta.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function")
                .getString(ARGUMENTS)
        );
    }

    @Test
    void writesValuesSelectedByProtocolIndices() {
        JSONObject root = new JSONObject(
            "{\"choices\":[{\"index\":4,\"delta\":{\"content\":\"original\","
                + "\"tool_calls\":[{\"index\":7,\"function\":{\"arguments\":\"original\"}}]}}]}"
        );
        ChatLane.direct(4, CONTENT).put(root, "text");
        ChatLane.tool(4, 7).put(root, ARGUMENTS);
        JSONObject delta = root.getJSONArray("choices").getJSONObject(0).getJSONObject("delta");
        assertEquals("text", delta.getString(CONTENT));
        assertEquals(
            ARGUMENTS, delta.getJSONArray("tool_calls").getJSONObject(0)
                .getJSONObject("function").getString(ARGUMENTS)
        );
    }

    @Test
    void clearsEveryStreamedValueInAReplayTemplate() {
        JSONObject root = root();
        JSONObject delta = root.getJSONArray("choices").getJSONObject(0).getJSONObject("delta");
        delta.put("reasoning_content", "reason");
        delta.put("reasoning", "thought");
        ChatLane.clear(root);
        assertEquals("", delta.getString(CONTENT));
        assertEquals("", delta.getString("reasoning_content"));
        assertEquals("", delta.getString("reasoning"));
        assertEquals(
            "", delta.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function")
                .getString(ARGUMENTS)
        );
    }

    @Test
    void ignoresSparseAndNonStringReplayFields() {
        ChatLane.clear(new JSONObject());
        ChatLane.clear(new JSONObject("{\"choices\":[null,{}, {\"delta\":{}}]}"));
        JSONObject sparse = new JSONObject(
            "{\"choices\":[{\"delta\":{\"content\":4,\"tool_calls\":[null,{},"
                + "{\"function\":{\"arguments\":4}}]}}]}"
        );
        ChatLane.clear(sparse);
        assertEquals(
            4, sparse.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("delta").getInt(CONTENT)
        );
        assertEquals(
            4, sparse.getJSONArray("choices").getJSONObject(0).getJSONObject("delta")
                .getJSONArray("tool_calls").getJSONObject(2).getJSONObject("function")
                .getInt(ARGUMENTS)
        );
    }

    @Test
    void rejectsAnIndexAbsentFromTheProtocolPayload() {
        JSONObject root = root();
        assertThrows(IllegalArgumentException.class, () -> ChatLane.direct(2, CONTENT).put(root, "text"));
        assertThrows(IllegalArgumentException.class, () -> ChatLane.tool(0, 2).put(root, "text"));
    }

    private static JSONObject root() {
        return new JSONObject(
            "{\"choices\":[{\"delta\":{\"content\":\"original\",\"tool_calls\":["
                + "{\"function\":{\"arguments\":\"original\"}}]}}]}"
        );
    }
}
