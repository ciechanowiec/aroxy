package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.client.ClientId;

/**
 * One listener as the daemon published it: which client it serves, the {@code host:port} it actually
 * bound, and the upstream it forwards to. The client is carried as its label rather than as a
 * {@link ClientId} because a snapshot may have been written by a build that serves a client this one
 * does not, and a snapshot must stay readable rather than fail to parse.
 *
 * @param client   the label of the client this listener serves
 * @param listen   the {@code host:port} this listener is bound to
 * @param upstream the upstream API base address this listener forwards to
 */
public record GatewayAddress(String client, String listen, String upstream) {
}
