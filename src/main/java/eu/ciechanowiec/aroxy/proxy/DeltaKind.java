package eu.ciechanowiec.aroxy.proxy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The two streamed {@code content_block_delta} flavours whose value this proxy reverses: a plain
 * {@code text_delta} (value field {@code text}) and a tool-use {@code input_json_delta} (value field
 * {@code partial_json}). The kind is remembered while a fragment is buffered so that, when a held
 * partial token is flushed as a synthetic event, the synthetic event matches the stream it came from
 * instead of always being emitted as a {@code text_delta}.
 */
@Getter
@RequiredArgsConstructor
enum DeltaKind {

    TEXT("text_delta", "text"),
    JSON("input_json_delta", "partial_json");

    private final String type;
    private final String field;

    static DeltaKind of(String block) {
        return block.contains("\"input_json_delta\"") ? JSON : TEXT;
    }
}
