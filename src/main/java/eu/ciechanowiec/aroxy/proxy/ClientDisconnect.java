package eu.ciechanowiec.aroxy.proxy;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Recognises the routine case where the connected agent closes the connection before the proxy
 * finishes writing the response - for example when a request is cancelled. Such a failure is benign:
 * it is not a proxy fault and must not be logged as an error or crash the handler. A real upstream
 * failure is distinguished so it can still surface as an error.
 */
@UtilityClass
final class ClientDisconnect {

    private static final List<String> SIGNS = List.of(
        "broken pipe", "connection reset", "connection closed", "stream was reset", "closed by"
    );
    private static final String EOF_TYPE = "EofException";

    static boolean isBenign(Throwable failure) {
        return isEofType(failure) || matchesMessage(failure);
    }

    private static boolean isEofType(Throwable failure) {
        return EOF_TYPE.equals(failure.getClass().getSimpleName());
    }

    private static boolean matchesMessage(Throwable failure) {
        return Optional.ofNullable(failure.getMessage())
            .map(message -> message.toLowerCase(Locale.ROOT))
            .filter(ClientDisconnect::mentionsDisconnect)
            .isPresent();
    }

    private static boolean mentionsDisconnect(String message) {
        return SIGNS.stream().anyMatch(message::contains);
    }
}
