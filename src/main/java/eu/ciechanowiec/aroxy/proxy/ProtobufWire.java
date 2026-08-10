package eu.ciechanowiec.aroxy.proxy;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import lombok.experimental.UtilityClass;

/**
 * Primitive protobuf wire-format reading and writing without message descriptors.
 */
@UtilityClass
final class ProtobufWire {

    private static final int FIXED_32_BYTES = Integer.BYTES;
    private static final int FIXED_64_BYTES = Long.BYTES;
    private static final int WIRE_MASK = 7;
    private static final int FIELD_SHIFT = 3;
    private static final int VARINT_SHIFT = 7;
    private static final int VARINT_DATA_MASK = 0x7F;
    private static final int VARINT_CONTINUATION = 0x80;

    static long fieldNumber(long key) {
        return key >>> FIELD_SHIFT;
    }

    static int wireType(long key) {
        return (int) (key & WIRE_MASK);
    }

    static Varint varint(byte[] input, int start) {
        long value = 0;
        int cursor = start;
        for (int shift = 0; shift < Long.SIZE && cursor < input.length; shift += VARINT_SHIFT) {
            int next = Byte.toUnsignedInt(input[cursor]);
            cursor++;
            value |= (long) (next & VARINT_DATA_MASK) << shift;
            if ((next & VARINT_CONTINUATION) == 0) {
                return new Varint(value, cursor);
            }
        }
        throw new InvalidProtobufException();
    }

    static int skip(byte[] input, int cursor, int wire) {
        return switch (wire) {
            case 0 -> varint(input, cursor).end();
            case 1 -> fixed(cursor, FIXED_64_BYTES, input.length);
            case 5 -> fixed(cursor, FIXED_32_BYTES, input.length);
            default -> throw new InvalidProtobufException();
        };
    }

    static int end(int start, long length, int limit) {
        if (length > Integer.MAX_VALUE) {
            throw new InvalidProtobufException();
        }
        return fixed(start, (int) length, limit);
    }

    static void writeVarint(ByteArrayOutputStream output, long value) {
        long remaining = value;
        while ((remaining & ~VARINT_DATA_MASK) != 0) {
            output.write((int) (remaining & VARINT_DATA_MASK) | VARINT_CONTINUATION);
            remaining >>>= VARINT_SHIFT;
        }
        output.write((int) remaining);
    }

    static byte[] slice(byte[] input, int start, int end) {
        return Arrays.copyOfRange(input, start, end);
    }

    private static int fixed(int cursor, int bytes, int limit) {
        int next = cursor + bytes;
        if (next < cursor || next > limit) {
            throw new InvalidProtobufException();
        }
        return next;
    }

    public record Varint(long value, int end) {
    }
}
