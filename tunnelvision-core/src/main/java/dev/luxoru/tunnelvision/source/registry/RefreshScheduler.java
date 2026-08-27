package dev.luxoru.tunnelvision.source.registry;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Refreshes sources in the background, each on its own schedule.
 */
public final class RefreshScheduler implements AutoCloseable {
    private final ScheduledExecutorService timer =
        Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("tunnelvision-scheduler").factory());
    private final ExecutorService workers =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("tunnelvision-refresh-", 0).factory());



    public void schedule(TrackedSource source, Duration delay) {
        if (timer.isShutdown()) {
            return;
        }
        timer.schedule(() -> workers.execute(() -> {
            boolean refreshed = source.refresh();
            schedule(source, source.nextRefreshDelay(refreshed));
        }), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        timer.shutdownNow();
        workers.shutdownNow();
    }
}
