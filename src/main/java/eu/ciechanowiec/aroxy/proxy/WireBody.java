package eu.ciechanowiec.aroxy.proxy;

/**
 * What a wire dump holds, which is what its file name announces.
 *
 * <p>A dump is opened by a person looking for what actually left the machine, and the extension is the
 * first thing that person reads. Claude Code and Codex carry JSON requests and text event streams, so
 * theirs say {@code .json} and {@code .txt} and are true. Cursor carries length-prefixed Connect
 * envelopes and bare protobuf messages, and naming those {@code .json} would be an editor full of
 * mojibake and a wasted minute before the reader concluded the file was not what it claimed.
 *
 * <p>The kind is passed in by the caller rather than guessed from the bytes. Whether a body is text is
 * a fact about the protocol that produced it, known for certain at the call site, and a sniff would
 * turn that certainty into a heuristic that a payload of unlucky bytes could get wrong.
 */
enum WireBody {

    /**
     * A body meant to be read as text: a JSON request, or a text event stream.
     */
    TEXT("-redacted-request.json", "-raw-response.txt"),
    /**
     * A body that is bytes: framed Connect envelopes, or a bare protobuf message.
     */
    BINARY("-redacted-request.bin", "-raw-response.bin");

    private final String request;
    private final String response;

    WireBody(String request, String response) {
        this.request = request;
        this.response = response;
    }

    /**
     * The file name a redacted-request dump of this kind takes.
     *
     * @param stamp the per-request stamp the dump is filed under
     * @return the dump file name
     */
    String requestFile(String stamp) {
        return stamp + this.request;
    }

    /**
     * The file name a raw-response dump of this kind takes.
     *
     * @param stamp the per-request stamp the dump is filed under
     * @return the dump file name
     */
    String responseFile(String stamp) {
        return stamp + this.response;
    }
}
