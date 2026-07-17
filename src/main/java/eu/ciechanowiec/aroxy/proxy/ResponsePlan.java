package eu.ciechanowiec.aroxy.proxy;

/**
 * How one upstream reply should be written back: whether it is the deobfuscated {@code /v1/messages}
 * path, whether that body is a streamed {@code text/event-stream}, and the per-request audit stamp.
 *
 * @param deobfuscate whether the body should be reversed at all
 * @param eventStream whether the upstream body is SSE
 * @param stamp       the per-request stamp correlating audit lines with wire dumps
 */
record ResponsePlan(boolean deobfuscate, boolean eventStream, String stamp) {

    boolean streamed() {
        return this.deobfuscate && this.eventStream;
    }
}
