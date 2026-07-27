package eu.ciechanowiec.aroxy.proxy;

/**
 * A malformed or unsupported Connect stream that must fail closed. The HTTP status is carried on the
 * exception rather than inferred from its wording, so rephrasing a message cannot change what the
 * client is told.
 */
final class ConnectProtocolException extends RuntimeException {

    private static final int BAD_REQUEST = 400;
    private static final int UNSUPPORTED_MEDIA_TYPE = 415;
    private final int status;

    ConnectProtocolException(String message) {
        this(message, BAD_REQUEST);
    }

    private ConnectProtocolException(String message, int status) {
        super(message);
        this.status = status;
    }

    /**
     * A stream whose media type or encoding aroxy cannot transform.
     *
     * @param message what was unsupported
     * @return the exception, reported as HTTP 415
     */
    static ConnectProtocolException unsupportedMedia(String message) {
        return new ConnectProtocolException(message, UNSUPPORTED_MEDIA_TYPE);
    }

    int status() {
        return this.status;
    }
}
