package eu.ciechanowiec.aroxy.daemon;

import eu.ciechanowiec.aroxy.config.AttachmentRegistry;
import eu.ciechanowiec.aroxy.config.EffectiveConfig;
import eu.ciechanowiec.aroxy.config.InvalidConfigException;
import eu.ciechanowiec.aroxy.config.Settings;
import eu.ciechanowiec.governance.Justification;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * The in-process daemon runner. {@link #run()} materializes the default config, builds the layered
 * {@link EffectiveConfig}, starts the proxy, publishes a {@link RuntimeSnapshot} for the CLI, and
 * starts a {@link ReloadWatcher} that reloads when the {@link ControlFiles} trigger is touched. There
 * is no control socket: the CLI drives the daemon entirely through state files and a stop signal. A
 * reload rebuilds the effective config from the default file, the persistent registry, and the
 * session temporaries file and swaps the live rules without rebinding the proxy socket. Temporaries
 * are cleared on start, so they never survive a restart, by design.
 */
@Slf4j
@Justification(
    "field/dependency counts are governed by the project's own PMD and Checkstyle caps"
)
@SuppressWarnings(
    {
        "PMD.AvoidSynchronizedAtMethodLevel", "PMD.AvoidUsingVolatile",
        "ClassWithTooManyFields", "ClassWithTooManyDependencies"
    }
)
public final class Daemon {

    private final Home home;
    private final AttachmentRegistry registry;
    private final ConfigAssembly assembly;
    private final LiveAttachments attachments;
    private final SnapshotPublisher publisher;
    private final CountDownLatch stopped;
    private volatile Optional<Running> running;

    /**
     * Creates a daemon over the given state directory, using the fixed production upstream.
     *
     * @param home the state directory layout the daemon reads and writes
     */
    public Daemon(Home home) {
        this(home, DaemonSeam.production());
    }

    /**
     * Creates a daemon over the given state directory with the given overrides, which are test-only:
     * {@link DaemonSeam#production()} overrides nothing, whereas a test binds a free port instead of
     * contending for the production one and forwards to an in-JVM stand-in instead of the real API.
     *
     * @param home the state directory layout the daemon reads and writes
     * @param seam the overrides to apply onto the assembled configuration
     */
    public Daemon(Home home, DaemonSeam seam) {
        this.home = home;
        this.registry = new AttachmentRegistry(home.attachments());
        this.assembly = new ConfigAssembly(home, this.registry, seam);
        this.attachments = new LiveAttachments();
        this.publisher = new SnapshotPublisher(home.runtimeFile(), System.currentTimeMillis());
        this.stopped = new CountDownLatch(1);
        this.running = Optional.empty();
    }

    /**
     * Starts the proxy and the reload watcher; returns once the proxy is bound and listening.
     *
     * @throws IOException if the proxy socket cannot be bound
     */
    public synchronized void run() throws IOException {
        new ControlFiles(this.home).clearTemporaries();
        EffectiveConfig config = this.assembly.build(this.temporaries());
        this.stampLoads();
        this.logLoadedFiles();
        LiveProxy live = LiveProxy.start(this.home, config);
        ReloadWatcher watcher = new ReloadWatcher(new ControlFiles(this.home), this::reloadFromSignal);
        watcher.start();
        this.running = Optional.of(new Running(live, watcher));
        this.publish(config, live);
        log.atInfo().addArgument(() -> listening(config.settings(), live)).log("aroxy daemon listening on {}");
    }

    private static String listening(Settings settings, LiveProxy live) {
        return live.addresses(settings).stream()
            .map(gateway -> gateway.client() + " on " + gateway.listen())
            .collect(Collectors.joining(", "));
    }

    private void logLoadedFiles() {
        this.registry.list().forEach(path -> log.info("aroxy loaded persistent config file {}", path));
    }

    /**
     * Blocks the calling thread until {@link #stop()} is invoked (used by the daemon entry).
     *
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public void awaitStop() throws InterruptedException {
        this.stopped.await();
    }

    /**
     * Rebuilds the effective config and swaps the live rule set; a no-op when not running.
     */
    public synchronized void reload() {
        this.running.ifPresent(this::applyReload);
    }

    private void applyReload(Running active) {
        EffectiveConfig config = this.assembly.build(this.temporaries());
        this.stampLoads();
        active.live().reload(config);
        this.publish(config, active.live());
        log.atInfo().addArgument(() -> config.rules().size()).log("aroxy daemon reloaded {} rule(s)");
    }

    private void reloadFromSignal() {
        try {
            this.reload();
        } catch (UncheckedIOException | InvalidConfigException exception) {
            log.warn("aroxy reload skipped, configuration unreadable: {}", exception.getMessage());
        }
    }

    /**
     * Stops the proxy and the reload watcher, clears the session temporaries so a later offline
     * {@code config} does not read a stopped session's attachments, and releases {@link #awaitStop()}.
     * Idempotent.
     */
    public synchronized void stop() {
        this.running.ifPresent(this::stopRunning);
        this.running = Optional.empty();
        new ControlFiles(this.home).clearTemporaries();
        this.stopped.countDown();
    }

    private void stopRunning(Running active) {
        log.info("aroxy daemon stopping");
        active.stop();
    }

    private void publish(EffectiveConfig config, LiveProxy live) {
        SnapshotData data = SnapshotData.from(
            live.addresses(config.settings()), config,
            this.attachmentInfos()
        );
        this.publisher.publish(data);
    }

    private List<AttachmentInfo> attachmentInfos() {
        List<AttachmentInfo> infos = new ArrayList<>();
        infos.add(this.info(this.home.defaultConfig(), AttachmentKind.DEFAULT));
        this.registry.list().forEach(path -> infos.add(this.info(path, AttachmentKind.PERSISTENT)));
        this.temporaries().forEach(path -> infos.add(this.info(path, AttachmentKind.TEMPORARY)));
        return infos;
    }

    private void stampLoads() {
        this.attachments.stamp(this.allPaths(), System.currentTimeMillis());
    }

    private List<Path> allPaths() {
        List<Path> paths = new ArrayList<>();
        paths.add(this.home.defaultConfig());
        paths.addAll(this.registry.list());
        paths.addAll(this.temporaries());
        return paths;
    }

    private List<Path> temporaries() {
        return new ControlFiles(this.home).readTemporaries();
    }

    private AttachmentInfo info(Path path, AttachmentKind kind) {
        return new AttachmentInfo(path.toString(), kind, this.attachments.loadedAt(path));
    }
}
