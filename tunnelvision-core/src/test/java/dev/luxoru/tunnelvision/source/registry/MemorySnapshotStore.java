package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.source.snapshot.SnapshotStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MemorySnapshotStore implements SnapshotStore {
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<Snapshot> load(String sourceId) {
        return Optional.ofNullable(snapshots.get(sourceId));
    }

    @Override
    public void save(String sourceId, Snapshot snapshot) {
        snapshots.put(sourceId, snapshot);
    }

    public Snapshot get(String sourceId) {
        return snapshots.get(sourceId);
    }

    public boolean has(String sourceId) {
        return snapshots.containsKey(sourceId);
    }
}
