package eu.ciechanowiec.aroxy.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PidRecordTest {

    private static final long PID = 42L;

    @Test
    void serializesAPidWithoutAnUnavailableStartTime() {
        assertEquals(Long.toString(PID), new PidRecord(PID, Optional.empty()).serialized());
    }
}
