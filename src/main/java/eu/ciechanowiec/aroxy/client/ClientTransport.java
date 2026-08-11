package eu.ciechanowiec.aroxy.client;

/**
 * The HTTP conversation shape used by a client. Buffered transports carry one complete request body
 * and may return SSE. Cursor's transport supports bidirectional HTTP/2 Connect streams and its
 * HTTP/1.1 compatibility methods.
 */
public sealed interface ClientTransport permits BufferedTransport, CopilotTransport, CursorTransport {
}
