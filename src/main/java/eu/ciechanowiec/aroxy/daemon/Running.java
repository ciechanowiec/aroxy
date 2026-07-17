package eu.ciechanowiec.aroxy.daemon;

/**
 * The parts that exist only while the daemon is up: the live proxy and the reload watcher that polls
 * the control-file trigger. Bundling them lets the {@link Daemon} hold one optional {@link Running}
 * for its started state and keeps its field count within the limit.
 *
 * @param live    the running proxy with its swappable obfuscator
 * @param watcher the reload-trigger poller
 */
record Running(LiveProxy live, ReloadWatcher watcher) {

    void stop() {
        this.watcher.stop();
        this.live.stop();
    }
}
