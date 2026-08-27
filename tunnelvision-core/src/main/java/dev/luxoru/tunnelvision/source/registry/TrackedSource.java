package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.detection.Detection;
import dev.luxoru.tunnelvision.net.IpBlock;
import dev.luxoru.tunnelvision.source.DetectionSource;
import dev.luxoru.tunnelvision.source.SourceContext;
import dev.luxoru.tunnelvision.source.guard.GuardSettings;
import dev.luxoru.tunnelvision.source.guard.SourceGuard;
import dev.luxoru.tunnelvision.source.snapshot.SnapshotStore;
import dev.luxoru.tunnelvision.util.CloseableLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One source, its current data and how its last refresh went.
 */
public final class TrackedSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackedSource.class);
    private static final int UNSAFE_EXAMPLES_LOGGED = 10;

    private final DetectionSource source;
    private final SourceContext context;
    private final SnapshotStore snapshots;
    private final GuardSettings guard;
    private final Clock clock;
    private final Runnable onChange;
    private final ReentrantLock refreshLock = new ReentrantLock();

    private volatile @Nullable SourceData data;
    private volatile @Nullable String lastError;
    private volatile boolean attempted;

    public TrackedSource(DetectionSource source, SourceContext context, SnapshotStore snapshots, GuardSettings guard,
                         Clock clock, Runnable onChange) {
        this.source = source;
        this.context = context;
        this.snapshots = snapshots;
        this.guard = guard;
        this.clock = clock;
        this.onChange = onChange;
    }

    public String id() {
        return source.id();
    }

    public boolean isStatic() {
        return source.refreshInterval().isZero();
    }

    public boolean isInitialized() {
        return data != null || attempted;
    }

    public void loadSnapshot() {
        try {
            Optional<SnapshotStore.Snapshot> snapshot = snapshots.load(source.id());
            if (snapshot.isEmpty()) {
                return;
            }
            // sapshots were checked when saved, so only drop entries that are invalid now
            SourceGuard.Result result = SourceGuard.check(snapshot.get().entries(), 0, guard, true);
            data = SourceData.of(result, snapshot.get().fetchedAt());
            LOGGER.info("Loaded {} entries for {} from its snapshot", result.accepted().size(), source.id());
        } catch (Exception ex) {
            LOGGER.warn("Couldn't load the snapshot of {}", source.id(), ex);
        }
    }

    public boolean refresh() {
        // One refresh per source at a time, e.g. a scheduled one and one requested through the APi
        try (var _ = new CloseableLock(refreshLock)) {
            return fetchAndApply();
        }
    }

    private boolean fetchAndApply() {
        Instant fetchedAt = clock.instant();
        try {
            List<String> fetched = source.fetch(context);
            SourceGuard.Result result = SourceGuard.check(fetched, previousEntries(), guard, source.trusted());
            if (result.rejected()) {
                lastError = "rejected: " + result.rejection();
                LOGGER.warn("rejected refresh of {}, keeping the previous data: {}", source.id(), result.rejection());
                return false;
            }
            apply(result, fetchedAt);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            LOGGER.warn("Couldnt refresh {}, keeping the previous data", source.id(), ex);
            return false;
        } finally {
            attempted = true;
        }
    }

    private void apply(SourceGuard.Result result, Instant fetchedAt) {
        List<String> unsafe = result.unsafe();
        if (!unsafe.isEmpty()) {
            LOGGER.warn("Dropped {} unsafe entries from {}: {}", unsafe.size(), source.id(),
                unsafe.subList(0, Math.min(UNSAFE_EXAMPLES_LOGGED, unsafe.size())));
        }
        data = SourceData.of(result, fetchedAt);
        lastError = null;
        if (!isStatic()) {
            saveSnapshot(result.accepted(), fetchedAt);
        }
        LOGGER.info("Refreshed {}: {} entries ({} invalid, {} unsafe)", source.id(), result.accepted().size(),
            result.invalid(), unsafe.size());
        onChange.run();
    }

    private void saveSnapshot(List<IpBlock> accepted, Instant fetchedAt) {
        List<String> entries = accepted.stream().map(IpBlock::toString).toList();
        try {
            snapshots.save(source.id(), new SnapshotStore.Snapshot(fetchedAt, entries));
        } catch (Exception ex) {
            LOGGER.warn("Couldnt save the snapshot of {}", source.id(), ex);
        }
    }

    private int previousEntries() {
        SourceData data = this.data;

        if(data == null) {
            return 0;
        }
        return data.entries();
    }

    /**
     * How long until this source should next refresh: now if it has no data, otherwise one interval
     * after its data was fetched.
     */
    public Duration untilDue(Instant now) {
        SourceData data = this.data;
        if (data == null) {
            return Duration.ZERO;
        }
        Duration delay = Duration.between(now, data.refreshedAt().plus(source.refreshInterval()));

        if(delay.isNegative()) {
            return Duration.ZERO;
        }

        return delay;
    }

    public Duration nextRefreshDelay(boolean refreshed) {
        Duration interval = source.refreshInterval();
        if (refreshed || guard.retryDelay().compareTo(interval) > 0) {
            return interval;
        }
        return guard.retryDelay();
    }

    public Optional<Detection> find(IpBlock address, Instant now) {
        SourceData data = this.data;
        if (data == null || isStale(data, now)) {
            return Optional.empty();
        }
        return data.index().find(address).map(match -> new Detection(source.id(), source.name(),
            source.category(), source.confidence(), match.toString()));
    }

    public SourceStatus status(Instant now) {
        SourceData data = this.data;
        if (data == null) {
            SourceStatus.State state = attempted ? SourceStatus.State.FAILED : SourceStatus.State.PENDING;
            return status(state, 0, 0, null);
        }
        SourceStatus.State state = isStale(data, now) ? SourceStatus.State.STALE : SourceStatus.State.ACTIVE;
        return status(state, data.entries(), data.index().size(), data.refreshedAt());
    }

    private SourceStatus status(SourceStatus.State state, int entries, int blocks, @Nullable Instant refreshedAt) {
        return new SourceStatus(source.id(), source.name(), source.category(), source.confidence(), state,
            entries, blocks, refreshedAt, lastError);
    }

    private boolean isStale(SourceData data, Instant now) {
        return !isStatic() && data.refreshedAt().plus(guard.staleAfter(source.refreshInterval())).isBefore(now);
    }
}
