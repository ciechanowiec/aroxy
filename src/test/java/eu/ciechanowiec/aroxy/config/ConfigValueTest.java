package eu.ciechanowiec.aroxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The scalar readers. The {@code listen} port reader ignores a host portion, accepts valid ports, and
 * rejects anything out of the {@code 1..65535} range - including {@code 0}, which would make the OS
 * bind a random ephemeral port instead of the fixed proxy port. The boolean reader accepts only
 * {@code true} and {@code false}: it reports anything else as unreadable rather than choosing for the
 * user, which is what lets a caller reject the file instead of quietly applying a default.
 */
class ConfigValueTest {

    private static final int PORT = 8788;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    @Test
    void acceptsAValidPortIgnoringTheHost() {
        assertEquals(Optional.of(PORT), ConfigValue.asPort("127.0.0.1:8788"), "host ignored, port parsed");
        assertEquals(Optional.of(PORT), ConfigValue.asPort("8788"), "a bare port is parsed");
    }

    @Test
    void rejectsPortZero() {
        assertTrue(ConfigValue.asPort("127.0.0.1:0").isEmpty(), "0 would bind a random ephemeral port");
        assertTrue(ConfigValue.asPort("0").isEmpty(), "a bare 0 is rejected too");
    }

    @Test
    void rejectsOutOfRangeOrGarbagePorts() {
        assertTrue(ConfigValue.asPort("70000").isEmpty(), "above 65535 is rejected");
        assertTrue(ConfigValue.asPort("nope").isEmpty(), "a non-numeric port is rejected");
    }

    @Test
    void acceptsTheLowestAndHighestValidPorts() {
        assertEquals(Optional.of(MIN_PORT), ConfigValue.asPort("1"), "port 1 is the inclusive lower bound");
        assertEquals(Optional.of(MAX_PORT), ConfigValue.asPort("65535"), "port 65535 is the inclusive upper bound");
    }

    @Test
    void readsBothBooleansInAnyCase() {
        assertEquals(Optional.of(true), ConfigValue.asBoolean("true"), "true is read");
        assertEquals(Optional.of(false), ConfigValue.asBoolean("false"), "false is read");
        assertEquals(Optional.of(true), ConfigValue.asBoolean("TRUE"), "the value is not case-sensitive");
    }

    @Test
    void reportsAnythingElseAsUnreadableRatherThanGuessing() {
        assertTrue(ConfigValue.asBoolean("yes").isEmpty(), "yes is not a boolean this format accepts");
        assertTrue(ConfigValue.asBoolean("1").isEmpty(), "1 is not read as true");
        assertTrue(ConfigValue.asBoolean("").isEmpty(), "an empty value names nothing");
    }

    @Test
    void namesWhatTheReadersAccept() {
        assertEquals("true or false", ConfigValue.booleanValues(), "a rejection can say what it wanted");
        assertEquals("a port between 1 and 65535", ConfigValue.portRange(), "a rejection can say what it wanted");
    }
}
