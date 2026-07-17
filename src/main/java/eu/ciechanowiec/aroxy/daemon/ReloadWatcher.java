package eu.ciechanowiec.aroxy.daemon;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polls the {@link ControlFiles} reload trigger on a fixed, short interval and runs the supplied
 * reload action whenever the trigger's stamp changes. This is the daemon's replacement for the former
 * {@code POST /reload} admin endpoint: a CLI command touches the trigger, and the next poll within the
 * interval applies the reload, so there is no control socket to reach. Polling is used rather than a
 * {@link java.nio.file.WatchService} because the platform watch service degrades to multi-second
 * polling on macOS, which would make {@code aroxy reload} feel unresponsive.
 */
final class ReloadWatcher {

    private static final long INTERVAL_MILLIS = 250L;
    private static final long NEVER = Long.MIN_VALUE;

    private final ControlFiles control;
    private final Runnable onReload;
    private final ScheduledExecutorService executor;
    private long lastStamp;
    private Optional<ScheduledFuture<?>> task;

    ReloadWatcher(ControlFiles control, Runnable onReload) {
        this.control = control;
        this.onReload = onReload;
        this.executor = Executors.newSingleThreadScheduledExecutor(ReloadWatcher::thread);
        this.lastStamp = NEVER;
        this.task = Optional.empty();
    }

    void start() {
        this.lastStamp = this.control.triggerStamp().orElse(NEVER);
        this.task = Optional.of(
            this.executor.scheduleAtFixedRate(
                this::tick, INTERVAL_MILLIS, INTERVAL_MILLIS, TimeUnit.MILLISECONDS
            )
        );
    }

    void stop() {
        this.task.ifPresent(scheduled -> scheduled.cancel(true));
        this.executor.shutdownNow();
    }

    private void tick() {
        long current = this.control.triggerStamp().orElse(this.lastStamp);
        if (current != this.lastStamp) {
            this.lastStamp = current;
            this.onReload.run();
        }
    }

    private static Thread thread(Runnable task) {
        Thread worker = new Thread(task, "aroxy-reload-watcher");
        worker.setDaemon(true);
        return worker;
    }
}
