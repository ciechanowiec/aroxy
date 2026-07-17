package eu.ciechanowiec.aroxy.daemon;

/**
 * One contributing configuration file as reported by {@code status} and {@code config list}: its
 * absolute path, its {@link AttachmentKind kind} (the bundled default, a persistent registry entry,
 * or a session temporary), and the epoch-millisecond time at which the running daemon first loaded
 * it.
 *
 * @param path           the absolute path of the file
 * @param kind           where the file comes from in the layered config
 * @param loadedAtMillis when the daemon first loaded the file, in epoch milliseconds
 */
public record AttachmentInfo(String path, AttachmentKind kind, long loadedAtMillis) {
}
