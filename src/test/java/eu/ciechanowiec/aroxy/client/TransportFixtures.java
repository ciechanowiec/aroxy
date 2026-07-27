package eu.ciechanowiec.aroxy.client;

import lombok.experimental.UtilityClass;

/**
 * Narrows a {@link ClientProfile}'s sealed {@link ClientTransport} for the tests that need one variant.
 *
 * <p>{@link ClientProfile} deliberately exposes only the sealed type, so there is no accessor that
 * compiles for every client and fails at runtime for Cursor. A test wanting the buffered shape narrows
 * it, and the narrowing lives here rather than once per test class: two copies of one switch are two
 * places to teach when a third transport arrives.
 */
@UtilityClass
final class TransportFixtures {

    /**
     * The buffered transport of a profile that has one.
     *
     * @param profile the profile to narrow
     * @return that profile's buffered transport
     */
    static BufferedTransport buffered(ClientProfile profile) {
        return switch (profile.transport()) {
            case BufferedTransport transport -> transport;
            case CursorTransport _ -> throw new AssertionError(profile.displayName() + " is not buffered");
        };
    }
}
