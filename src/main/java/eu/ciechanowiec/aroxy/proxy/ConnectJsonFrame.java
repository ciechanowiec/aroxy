package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.governance.Justification;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * One Connect protocol envelope: a flag byte, a four-byte network-order length, and a JSON payload.
 *
 * @param flags   the Connect envelope flags
 * @param payload the envelope payload
 */
@Justification(
    "the payload is cloned by the compact constructor and again by the accessor, so the array never escapes"
)
@SuppressWarnings(
    {"ArrayRecordComponent", "AssignmentOrReturnOfFieldWithMutableType"}
)
record ConnectJsonFrame(int flags, byte[] payload) {

    static final int END_STREAM = 2;
    private static final int HEADER = 5;
    private static final int MAX_PAYLOAD = 64 * 1024 * 1024;

    ConnectJsonFrame {
        payload = payload.clone();
    }

    static Optional<ConnectJsonFrame> read(InputStream input) throws IOException {
        int flags = input.read();
        Optional<ConnectJsonFrame> frame = Optional.empty();
        if (flags >= 0) {
            byte[] lengthBytes = input.readNBytes(HEADER - 1);
            long length = readLength(lengthBytes);
            byte[] payload = input.readNBytes((int) length);
            verifyPayload(payload, length);
            frame = Optional.of(new ConnectJsonFrame(flags, payload));
        }
        return frame;
    }

    private static long readLength(byte[] lengthBytes) {
        if (lengthBytes.length != HEADER - 1) {
            throw new ConnectProtocolException("Truncated Connect envelope header");
        }
        long length = Integer.toUnsignedLong(ByteBuffer.wrap(lengthBytes).getInt());
        if (length > MAX_PAYLOAD) {
            throw new ConnectProtocolException("Connect envelope exceeds 64 MiB");
        }
        return length;
    }

    private static void verifyPayload(byte[] payload, long length) {
        if (payload.length != length) {
            throw new ConnectProtocolException("Truncated Connect envelope payload");
        }
    }

    /**
     * This envelope as it travels on the wire, header included.
     *
     * <p>A wire dump of a stream is a concatenation of these, so it has to carry the length prefixes
     * too. Without them the dump is one undelimited run of bytes that cannot be split back into the
     * envelopes it was made of, which for a protobuf stream leaves it unreadable altogether
     *
     * @return the framed envelope
     */
    byte[] bytes() {
        ByteArrayOutputStream framed = new ByteArrayOutputStream(HEADER + this.payload.length);
        framed.write(this.flags);
        framed.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(this.payload.length).array());
        framed.writeBytes(this.payload);
        return framed.toByteArray();
    }

    void write(OutputStream output) throws IOException {
        output.write(this.bytes());
        output.flush();
    }

    boolean compressed() {
        return (this.flags & 1) != 0;
    }

    boolean terminal() {
        return (this.flags & END_STREAM) != 0;
    }

    @Override
    public byte[] payload() {
        return this.payload.clone();
    }
}
