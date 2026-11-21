package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.client.CursorJsonShape;
import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;
import eu.ciechanowiec.aroxy.obfuscation.Reversal;
import eu.ciechanowiec.aroxy.obfuscation.StreamDeobfuscator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jspecify.annotations.Nullable;

/**
 * Restores Cursor response frames, retaining partial aroxy tokens across consecutive deltas.
 */
final class CursorResponseStream {

    private static final String SENTINEL = "aroxy-fragment-sentinel";
    private final CursorJsonShape shape;
    private final Obfuscator obfuscator;
    private final StreamDeobfuscator fragments;
    /**
     * The delta most recently restored, the key it sat under, and the channel it belongs to. One
     * response is decoded by one thread, so these references hold sequential state. They are atomic
     * so the fields can be final, not because this class is safe to share.
     */
    private final AtomicReference<Optional<JSONObject>> lastFragment;
    private final AtomicReference<String> lastKey;
    private final AtomicReference<String> channel;

    private final AtomicInteger reversed;

    CursorResponseStream(CursorJsonShape shape, Obfuscator obfuscator) {
        this.shape = shape;
        this.obfuscator = obfuscator;
        this.fragments = new StreamDeobfuscator(obfuscator);
        this.lastFragment = new AtomicReference<>(Optional.empty());
        this.lastKey = new AtomicReference<>("");
        this.channel = new AtomicReference<>("");
        this.reversed = new AtomicInteger();
    }

    CursorResponseBatch accept(ConnectJsonFrame frame) {
        if (frame.compressed()) {
            throw ConnectProtocolException.unsupportedMedia(
                "Compressed Connect envelopes are unsupported"
            );
        }
        JSONObject object = object(frame);
        Optional<FragmentField> field = this.find(object, "");
        List<ConnectJsonFrame> output = new ArrayList<>();
        boolean ending = frame.terminal() || this.terminal(object);
        this.flushBeforeWhole(ending, field, output);
        field.ifPresentOrElse(
            found -> this.fragment(frame, object, found, output),
            () -> this.whole(frame, object, output)
        );
        this.flushAfterFragment(ending, field, output);
        boolean complete = frame.terminal() || this.turnEnded(object);
        return new CursorResponseBatch(output, complete);
    }

    List<ConnectJsonFrame> finish() {
        List<ConnectJsonFrame> output = new ArrayList<>();
        this.flush(output);
        return output;
    }

    int reversedCount() {
        return this.reversed.get() + this.fragments.reversedCount();
    }

    private void flushBeforeWhole(
        boolean ending, Optional<FragmentField> field, Collection<ConnectJsonFrame> output
    ) {
        if (ending && field.isEmpty()) {
            this.flush(output);
        }
    }

    private void flushAfterFragment(
        boolean ending, Optional<FragmentField> field, Collection<ConnectJsonFrame> output
    ) {
        if (ending && field.isPresent()) {
            this.flush(output);
        }
    }

    private void fragment(
        ConnectJsonFrame frame, JSONObject object, FragmentField field,
        Collection<ConnectJsonFrame> output
    ) {
        String open = Objects.requireNonNullElse(this.channel.get(), "");
        if (!open.isEmpty() && !open.equals(field.channel())) {
            this.flush(output);
        }
        field.parent().put(field.key(), SENTINEL);
        Reversal whole = new JsonReversal(this.obfuscator).apply(object);
        this.reversed.addAndGet(whole.count());
        JSONObject restored = new JSONObject(whole.text());
        FragmentField restoredField = this.find(restored, "").orElseThrow();
        String safe = this.fragments.accept(field.value());
        restoredField.parent().put(restoredField.key(), safe);
        this.lastFragment.set(Optional.of(new JSONObject(restored.toString())));
        this.lastKey.set(restoredField.key());
        this.channel.set(field.channel());
        output.add(jsonFrame(frame.flags(), restored));
    }

    private void whole(ConnectJsonFrame frame, JSONObject object, Collection<ConnectJsonFrame> output) {
        Reversal whole = new JsonReversal(this.obfuscator).apply(object);
        this.reversed.addAndGet(whole.count());
        output.add(new ConnectJsonFrame(frame.flags(), whole.text().getBytes(StandardCharsets.UTF_8)));
    }

    private void flush(Collection<ConnectJsonFrame> output) {
        String held = this.fragments.flush();
        Optional<JSONObject> previous = Objects.requireNonNullElse(this.lastFragment.get(), Optional.empty());
        if (!held.isEmpty() && previous.isPresent()) {
            JSONObject fragment = previous.orElseThrow();
            FragmentField replay = this.find(fragment, "").orElseThrow();
            replay.parent().put(Objects.requireNonNullElse(this.lastKey.get(), ""), held);
            output.add(jsonFrame(0, fragment));
        }
        this.channel.set("");
    }

    private static JSONObject object(ConnectJsonFrame frame) {
        Object value = new JSONTokener(new String(frame.payload(), StandardCharsets.UTF_8)).nextValue();
        if (value instanceof JSONObject object) {
            return object;
        }
        throw new ConnectProtocolException("Cursor response envelope must carry a JSON object");
    }

    private Optional<FragmentField> find(Object value, String parentKey) {
        return this.findObject(value, parentKey).or(() -> this.findArray(value, parentKey));
    }

    private Optional<FragmentField> findObject(Object value, String parentKey) {
        Optional<FragmentField> found = Optional.empty();
        if (value instanceof JSONObject object) {
            Optional<FragmentField> direct = this.direct(object, parentKey);
            found = direct.isPresent() ? direct : this.descend(object);
        }
        return found;
    }

    private Optional<FragmentField> findArray(Object value, String parentKey) {
        Optional<FragmentField> found = Optional.empty();
        if (value instanceof JSONArray array) {
            found = this.descend(array, parentKey);
        }
        return found;
    }

    private Optional<FragmentField> direct(JSONObject object, String parentKey) {
        String key = this.fragmentKey(parentKey, object);
        return key.isEmpty()
            ? Optional.empty()
            : Optional.of(new FragmentField(object, key, object.getString(key), parentKey));
    }

    private String fragmentKey(String parentKey, JSONObject object) {
        return Optional.ofNullable(this.shape.fragmentFields().get(parentKey))
            .filter(key -> object.opt(key) instanceof String)
            .orElse("");
    }

    private Optional<FragmentField> descend(JSONObject object) {
        Optional<FragmentField> result = Optional.empty();
        for (String key : object.keySet()) {
            Optional<FragmentField> found = this.find(object.get(key), key);
            if (found.isPresent()) {
                result = found;
                break;
            }
        }
        return result;
    }

    private Optional<FragmentField> descend(JSONArray array, String parentKey) {
        Optional<FragmentField> result = Optional.empty();
        for (int index = 0; index < array.length(); index++) {
            Optional<FragmentField> found = this.find(array.get(index), parentKey);
            if (found.isPresent()) {
                result = found;
                break;
            }
        }
        return result;
    }

    // Searching the parsed tree for a key, rather than the serialized text for a quoted name, keeps a
    // value that merely reads like an event name from ending the turn - and spares the stream a full
    // re-serialization of every delta frame just to run a substring scan over it.
    private boolean terminal(JSONObject object) {
        return hasKey(object, this.shape.flushKeys());
    }

    private boolean turnEnded(JSONObject object) {
        return hasKey(object, Set.of(this.shape.turnEndKey()));
    }

    private static boolean hasKey(@Nullable Object value, Set<String> keys) {
        return switch (value) {
            case JSONObject object -> object.keySet().stream().anyMatch(keys::contains)
                || object.keySet().stream().anyMatch(key -> hasKey(object.get(key), keys));
            case JSONArray array -> IntStream.range(0, array.length())
                .anyMatch(index -> hasKey(array.get(index), keys));
            case null, default -> false;
        };
    }

    private static ConnectJsonFrame jsonFrame(int flags, JSONObject object) {
        return new ConnectJsonFrame(flags, object.toString().getBytes(StandardCharsets.UTF_8));
    }

    private record FragmentField(JSONObject parent, String key, String value, String channel) {
    }
}
