package dev.luxoru.tunnelvision.source.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSnapshotStoreTest {
    @TempDir
    Path directory;

    @Test
    void roundTrips() throws Exception {
        FileSnapshotStore store = new FileSnapshotStore(directory.resolve("nested"));
        SnapshotStore.Snapshot snapshot = new SnapshotStore.Snapshot(Instant.parse("2026-10-15T12:00:00Z"), List.of("1.2.3.4", "5.6.7.0/24"));
        store.save("tor", snapshot);
        assertThat(store.load("tor")).hasValue(snapshot);
        assertThat(store.load("missing")).isEmpty();
    }

    @Test
    void ignoresCorruptFiles() throws Exception {
        Files.writeString(directory.resolve("tor.txt"), "1.2.3.4\n");
        assertThat(new FileSnapshotStore(directory).load("tor")).isEmpty();
    }

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> new FileSnapshotStore(directory).load("../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }
}
