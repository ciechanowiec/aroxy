package eu.ciechanowiec.aroxy.proxy;

import eu.ciechanowiec.aroxy.obfuscation.Obfuscator;

/**
 * The collaborators the proxy needs to handle a request: the {@link Obfuscator} that rewrites bodies
 * in both directions, the {@link Upstream} that forwards to the Claude API, and the {@link AuditLog}
 * that records proof artifacts. Bundling them keeps the wiring constructors within the parameter
 * limit while staying explicit about what the proxy depends on.
 *
 * @param obfuscator the engine used to obfuscate requests and deobfuscate responses
 * @param upstream   the forwarder to the Claude API
 * @param audit      the audit log and wire-dump writer
 */
public record ProxyParts(Obfuscator obfuscator, Upstream upstream, AuditLog audit) {
}
