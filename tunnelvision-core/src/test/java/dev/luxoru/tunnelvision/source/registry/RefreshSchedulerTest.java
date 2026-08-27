package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.source.SourceContext;
import dev.luxoru.tunnelvision.source.guard.GuardSettings;
import dev.luxoru.tunnelvision.source.snapshot.SnapshotStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshSchedulerTest {
    private static TrackedSource track(FakeSource source) {
        return new TrackedSource(source, SourceContext.defaults(), SnapshotStore.none(), GuardSettings.DEFAULTS,
            Clock.systemUTC(), () -> {});
    }

    @Test
    void keepsRefreshingEveryInterval() throws Exception {
        FakeSource source = new FakeSource(Duration.ofMillis(20));
        CountDownLatch threeRefreshes = new CountDownLatch(3);
        source.onFetch(threeRefreshes::countDown);

        try (RefreshScheduler scheduler = new RefreshScheduler()) {
            scheduler.schedule(track(source), Duration.ZERO);
            assertThat(threeRefreshes.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void keepsRetryingWhenRefreshesFail() throws Exception {
        FakeSource source = new FakeSource(Duration.ofMillis(20)); // Shorter than the retry delay, so it wins
        source.setFailing(true);
        CountDownLatch threeAttempts = new CountDownLatch(3);
        source.onFetch(threeAttempts::countDown);

        try (RefreshScheduler scheduler = new RefreshScheduler()) {
            scheduler.schedule(track(source), Duration.ZERO);
            assertThat(threeAttempts.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void waitsForTheDelay() throws Exception {
        FakeSource source = new FakeSource();
        CountDownLatch refreshed = new CountDownLatch(1);
        source.onFetch(refreshed::countDown);

        try (RefreshScheduler scheduler = new RefreshScheduler()) {
            scheduler.schedule(track(source), Duration.ofHours(1));
            assertThat(refreshed.await(200, TimeUnit.MILLISECONDS)).isFalse();
        }
    }

    @Test
    void stopsOnceClosed() throws Exception {
        FakeSource source = new FakeSource(Duration.ofMillis(20));
        CountDownLatch firstRefresh = new CountDownLatch(1);
        source.onFetch(firstRefresh::countDown);

        RefreshScheduler scheduler = new RefreshScheduler();
        scheduler.schedule(track(source), Duration.ZERO);
        assertThat(firstRefresh.await(5, TimeUnit.SECONDS)).isTrue();
        scheduler.close();

        int fetchesAtClose = source.fetches();
        Thread.sleep(200); // Ten intervals, enough for refreshes to show up if any were still scheduled
        assertThat(source.fetches()).isLessThanOrEqualTo(fetchesAtClose + 1); // One may have been miflight
        scheduler.schedule(track(source), Duration.ZERO);
    }
}
