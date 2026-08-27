package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.net.IpBlock;
import dev.luxoru.tunnelvision.source.DetectionSource;
import dev.luxoru.tunnelvision.source.SourceContext;
import dev.luxoru.tunnelvision.source.guard.GuardSettings;
import dev.luxoru.tunnelvision.source.list.StaticSource;
import dev.luxoru.tunnelvision.source.snapshot.SnapshotStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TrackedSourceTest {
    private static final IpBlock ADDRESS = IpBlock.parse("45.1.2.3").orElseThrow();

    private final FakeSource source = new FakeSource();
    private final MemorySnapshotStore snapshots = new MemorySnapshotStore();
    private final MutableClock clock = new MutableClock();
    private final AtomicInteger changes = new AtomicInteger();

    private TrackedSource track(DetectionSource source) {
        return new TrackedSource(source, SourceContext.defaults(), snapshots, GuardSettings.DEFAULTS, clock,
            changes::incrementAndGet);
    }

    @Test
    void isPendingUntilTheFirstRefresh() {
        TrackedSource tracked = track(source);
        assertThat(tracked.isInitialized()).isFalse();
        assertThat(tracked.status(clock.instant()).state()).isEqualTo(SourceStatus.State.PENDING);
        assertThat(tracked.find(ADDRESS, clock.instant())).isEmpty();
    }

    @Test
    void aSuccessfulRefreshSwapsInDataSavesASnapshotAndNotifies() {
        TrackedSource tracked = track(source);
        assertThat(tracked.refresh()).isTrue();

        assertThat(tracked.find(ADDRESS, clock.instant())).hasValueSatisfying(detection -> {
            assertThat(detection.source()).isEqualTo("fake");
            assertThat(detection.match()).isEqualTo("45.1.2.0/24");
        });
        SourceStatus status = tracked.status(clock.instant());
        assertThat(status.state()).isEqualTo(SourceStatus.State.ACTIVE);
        assertThat(status.entries()).isEqualTo(1);
        assertThat(status.refreshedAt()).isEqualTo(clock.instant());
        assertThat(status.lastError()).isNull();
        assertThat(snapshots.get("fake").entries()).containsExactly("45.1.2.0/24");
        assertThat(changes).hasValue(1);
    }

    @Test
    void aFirstRefreshThatFailsIsFailed() {
        source.setFailing(true);
        TrackedSource tracked = track(source);
        assertThat(tracked.refresh()).isFalse();
        assertThat(tracked.isInitialized()).isTrue(); // It tried, so it no longer holds up readiness
        SourceStatus status = tracked.status(clock.instant());
        assertThat(status.state()).isEqualTo(SourceStatus.State.FAILED);
        assertThat(status.lastError()).isEqualTo("IOException: upstream down");
        assertThat(changes).hasValue(0);
    }

    @Test
    void aFailedRefreshKeepsThePreviousData() {
        TrackedSource tracked = track(source);
        tracked.refresh();
        source.setFailing(true);
        assertThat(tracked.refresh()).isFalse();
        assertThat(tracked.find(ADDRESS, clock.instant())).isPresent();
        assertThat(tracked.status(clock.instant()).state()).isEqualTo(SourceStatus.State.ACTIVE);
    }

    @Test
    void aRejectedRefreshKeepsThePreviousData() {
        TrackedSource tracked = track(source);
        tracked.refresh();
        source.setEntries(List.of("garbage"));
        assertThat(tracked.refresh()).isFalse();
        assertThat(tracked.find(ADDRESS, clock.instant())).isPresent();
        assertThat(tracked.status(clock.instant()).lastError()).startsWith("rejected");
        assertThat(changes).hasValue(1);
    }

    @Test
    void aSuccessfulRefreshClearsTheLastError() {
        TrackedSource tracked = track(source);
        source.setFailing(true);
        tracked.refresh();
        source.setFailing(false);
        tracked.refresh();
        assertThat(tracked.status(clock.instant()).lastError()).isNull();
    }

    @Test
    void staleDataIsIgnored() {
        TrackedSource tracked = track(source);
        tracked.refresh();
        clock.advance(Duration.ofHours(25)); // Stale after 3 intervals (3h), but never before 24h
        assertThat(tracked.find(ADDRESS, clock.instant())).isEmpty();
        assertThat(tracked.status(clock.instant()).state()).isEqualTo(SourceStatus.State.STALE);
    }

    @Test
    void staticSourcesNeverGoStaleOrSaveSnapshots() {
        TrackedSource tracked = track(new StaticSource("deny", "Denylist", Category.ABUSE, Confidence.CONFIRMED,
            List.of("45.1.2.3")));
        assertThat(tracked.isStatic()).isTrue();
        tracked.refresh();
        clock.advance(Duration.ofDays(365));
        assertThat(tracked.find(ADDRESS, clock.instant())).isPresent();
        assertThat(snapshots.has("deny")).isFalse();
    }

    @Test
    void loadsItsSnapshot() {
        snapshots.save("fake", new SnapshotStore.Snapshot(clock.instant(), List.of("45.1.2.0/24", "garbage")));
        TrackedSource tracked = track(source);
        tracked.loadSnapshot();
        assertThat(tracked.find(ADDRESS, clock.instant())).isPresent();
        assertThat(tracked.status(clock.instant()).entries()).isEqualTo(1); // The invalid entry is dropped
        assertThat(source.fetches()).isZero();
    }

    @Test
    void aBrokenSnapshotStoreIsLoggedNotThrown() {
        SnapshotStore broken = new SnapshotStore() {
            @Override
            public Optional<Snapshot> load(String sourceId) throws IOException {
                throw new IOException("disk");
            }

            @Override
            public void save(String sourceId, Snapshot snapshot) throws IOException {
                throw new IOException("disk");
            }
        };
        TrackedSource tracked = new TrackedSource(source, SourceContext.defaults(), broken, GuardSettings.DEFAULTS,
            clock, () -> {});
        tracked.loadSnapshot();
        assertThat(tracked.refresh()).isTrue(); // Saving fails, but the refresh still counts
    }

    @Test
    void isDueNowWithoutDataOtherwiseOneIntervalAfterItsData() {
        TrackedSource tracked = track(source);
        assertThat(tracked.untilDue(clock.instant())).isZero();

        tracked.refresh();
        assertThat(tracked.untilDue(clock.instant())).isEqualTo(Duration.ofHours(1));
        clock.advance(Duration.ofMinutes(45));
        assertThat(tracked.untilDue(clock.instant())).isEqualTo(Duration.ofMinutes(15));
        clock.advance(Duration.ofHours(2));
        assertThat(tracked.untilDue(clock.instant())).isZero();
    }

    @Test
    void retriesSoonerAfterAFailure() {
        TrackedSource tracked = track(source);
        assertThat(tracked.nextRefreshDelay(true)).isEqualTo(Duration.ofHours(1));
        assertThat(tracked.nextRefreshDelay(false)).isEqualTo(GuardSettings.DEFAULTS.retryDelay());

        // Never retry later than the normal interval
        TrackedSource frequent = track(new FakeSource(Duration.ofMinutes(1)));
        assertThat(frequent.nextRefreshDelay(false)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void refreshesOfOneSourceNeverOverlap() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        CountDownLatch bothStarted = new CountDownLatch(2);
        source.onFetch(() -> {
            mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
        });
        TrackedSource tracked = track(source);

        Thread first = Thread.ofVirtual().start(() -> {
            bothStarted.countDown();
            tracked.refresh();
        });
        Thread second = Thread.ofVirtual().start(() -> {
            bothStarted.countDown();
            tracked.refresh();
        });
        assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
        first.join();
        second.join();

        assertThat(source.fetches()).isEqualTo(2);
        assertThat(mostAtOnce).hasValue(1);
    }
}
