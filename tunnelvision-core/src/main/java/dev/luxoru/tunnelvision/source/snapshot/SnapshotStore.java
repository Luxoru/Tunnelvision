package dev.luxoru.tunnelvision.source.snapshot;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Keeps the last good copy of each source, so a restart doesn't depend on upstream being reachable.
 */
public interface SnapshotStore {

    Optional<Snapshot> load(String sourceId) throws IOException;

    void save(String sourceId, Snapshot snapshot) throws IOException;

    static SnapshotStore none() {
        return new SnapshotStore() {
            @Override
            public Optional<Snapshot> load(String sourceId) {
                return Optional.empty();
            }

            @Override
            public void save(String sourceId, Snapshot snapshot) {}
        };
    }

    record Snapshot(Instant fetchedAt, List<String> entries) {
    }
}
