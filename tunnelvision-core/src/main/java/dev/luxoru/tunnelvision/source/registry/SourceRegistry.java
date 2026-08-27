package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.detection.Detection;
import dev.luxoru.tunnelvision.net.IpBlock;
import dev.luxoru.tunnelvision.source.DetectionSource;
import dev.luxoru.tunnelvision.source.SourceContext;
import dev.luxoru.tunnelvision.source.Sources;
import dev.luxoru.tunnelvision.source.guard.GuardSettings;
import dev.luxoru.tunnelvision.source.snapshot.SnapshotStore;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds every source's data in memory, keeps it refreshed, and answers lookups against it.
 */
public final class SourceRegistry implements AutoCloseable {
    private final Map<String, TrackedSource> sources = new LinkedHashMap<>();
    private final Clock clock;
    private @Nullable RefreshScheduler scheduler;

    public SourceRegistry(List<DetectionSource> sources, SourceContext context, SnapshotStore snapshots,
                          GuardSettings guard, Clock clock, Runnable onChange) {
        for (DetectionSource source : sources) {
            Sources.validateId(source.id());
            TrackedSource tracked = new TrackedSource(source, context, snapshots, guard, clock, onChange);
            if (this.sources.putIfAbsent(source.id(), tracked) != null) {
                throw new IllegalArgumentException("Duplicate source id " + source.id());
            }
        }
        this.clock = clock;
    }

    public void load() {
        for (TrackedSource source : sources.values()) {
            if (source.isStatic()) {
                source.refresh();
            } else {
                source.loadSnapshot();
            }
        }
    }

    /**
     * Refresh each source in the background when it's due, and again every refresh interval.
     */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = new RefreshScheduler();
        Instant now = clock.instant();
        for (TrackedSource source : sources.values()) {
            if (!source.isStatic()) {
                scheduler.schedule(source, source.untilDue(now));
            }
        }
    }

    public boolean refresh(String sourceId) {
        TrackedSource source = sources.get(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("Unknown source " + sourceId);
        }
        return source.refresh();
    }

    /**
     * Find every source that contains the given address, ignoring stale sources.
     *
     * @param address the address
     * @return the detections
     */
    public List<Detection> lookup(IpBlock address) {
        Instant now = clock.instant();
        List<Detection> detections = new ArrayList<>(2);
        for (TrackedSource source : sources.values()) {
            source.find(address, now).ifPresent(detections::add);
        }
        return detections;
    }

    public boolean initialized() {
        return sources.values().stream().allMatch(TrackedSource::isInitialized);
    }

    public List<SourceStatus> status() {
        Instant now = clock.instant();
        return sources.values().stream()
                .map(source -> source.status(now)).toList();
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            scheduler.close();
        }
    }
}
