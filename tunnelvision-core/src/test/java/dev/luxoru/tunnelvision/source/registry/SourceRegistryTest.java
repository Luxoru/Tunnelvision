package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.net.IpBlock;
import dev.luxoru.tunnelvision.source.SourceContext;
import dev.luxoru.tunnelvision.source.guard.GuardSettings;
import dev.luxoru.tunnelvision.source.list.StaticSource;
import dev.luxoru.tunnelvision.source.snapshot.SnapshotStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SourceRegistryTest {
    private static final IpBlock ADDRESS = IpBlock.parse("45.1.2.3").orElseThrow();

    @Test
    void refreshesAndLooksUp() {
        FakeSource source = new FakeSource();
        AtomicInteger changes = new AtomicInteger();
        MemorySnapshotStore snapshots = new MemorySnapshotStore();
        SourceRegistry registry = new SourceRegistry(List.of(source), SourceContext.defaults(), snapshots,
            GuardSettings.DEFAULTS, Clock.systemUTC(), changes::incrementAndGet);

        assertThat(registry.initialized()).isFalse();
        assertThat(registry.lookup(ADDRESS)).isEmpty();
        assertThat(registry.refresh("fake")).isTrue();
        assertThat(registry.initialized()).isTrue();
        assertThat(registry.lookup(ADDRESS)).singleElement().satisfies(detection -> {
            assertThat(detection.source()).isEqualTo("fake");
            assertThat(detection.match()).isEqualTo("45.1.2.0/24");
        });
        assertThat(changes).hasValue(1);
        assertThat(snapshots.get("fake").entries()).containsExactly("45.1.2.0/24");
    }

    @Test
    void keepsPreviousDataWhenARefreshFails() {
        FakeSource source = new FakeSource();
        SourceRegistry registry = new SourceRegistry(List.of(source), SourceContext.defaults(), SnapshotStore.none(),
            GuardSettings.DEFAULTS, Clock.systemUTC(), () -> {});
        registry.refresh("fake");

        source.setFailing(true);
        assertThat(registry.refresh("fake")).isFalse();
        assertThat(registry.lookup(ADDRESS)).hasSize(1);
        assertThat(registry.status().getFirst().lastError()).contains("upstream down");

        source.setFailing(false);
        source.setEntries(List.of("garbage"));
        assertThat(registry.refresh("fake")).isFalse();
        assertThat(registry.lookup(ADDRESS)).hasSize(1);
        assertThat(registry.status().getFirst().lastError()).startsWith("rejected");
    }

    @Test
    void loadsSnapshotsAndIgnoresStaleData() {
        MutableClock clock = new MutableClock();
        MemorySnapshotStore snapshots = new MemorySnapshotStore();
        snapshots.save("fake", new SnapshotStore.Snapshot(clock.instant(), List.of("45.1.2.0/24")));
        SourceRegistry registry = new SourceRegistry(List.of(new FakeSource()), SourceContext.defaults(), snapshots,
            GuardSettings.DEFAULTS, clock, () -> {});
        registry.load();
        assertThat(registry.lookup(ADDRESS)).hasSize(1);
        assertThat(registry.status().getFirst().state()).isEqualTo(SourceStatus.State.ACTIVE);

        clock.advance(Duration.ofHours(25)); // 3 intervals is 3h, but never stale before 24h
        assertThat(registry.lookup(ADDRESS)).isEmpty();
        assertThat(registry.status().getFirst().state()).isEqualTo(SourceStatus.State.STALE);
    }

    @Test
    void loadsStaticSourcesImmediately() {
        SourceRegistry registry = new SourceRegistry(
            List.of(new StaticSource("deny", "Denylist", Category.ABUSE, Confidence.CONFIRMED, List.of("45.1.2.3"))),
            SourceContext.defaults(), SnapshotStore.none(), GuardSettings.DEFAULTS, Clock.systemUTC(), () -> {});
        registry.load();
        assertThat(registry.initialized()).isTrue();
        assertThat(registry.lookup(ADDRESS)).hasSize(1);
    }
}
