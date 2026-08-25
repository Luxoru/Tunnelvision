package dev.luxoru.tunnelvision.source.snapshot;

import dev.luxoru.tunnelvision.source.Sources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores snapshots as plain-text files, {@code <directory>/<source id>.txt}.
 */
public final class FileSnapshotStore implements SnapshotStore {
    private static final String HEADER = "# fetched ";

    private final Path directory;

    public FileSnapshotStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public Optional<Snapshot> load(String sourceId) throws IOException {
        Path file = file(sourceId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.getFirst().startsWith(HEADER)) {
            return Optional.empty();
        }
        Instant fetchedAt;
        try {
            fetchedAt = Instant.parse(lines.getFirst().substring(HEADER.length()).strip());
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
        return Optional.of(new Snapshot(fetchedAt, lines.subList(1, lines.size())));
    }

    @Override
    public void save(String sourceId, Snapshot snapshot) throws IOException {
        Files.createDirectories(directory);
        List<String> lines = new ArrayList<>(snapshot.entries().size() + 1);
        lines.add(HEADER + snapshot.fetchedAt());
        lines.addAll(snapshot.entries());
        // Write then wee move
        Path temp = Files.createTempFile(directory, sourceId, ".tmp");
        try {
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, file(sourceId), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UncheckedIOException ex) {
            Files.deleteIfExists(temp);
            throw ex;
        }
    }

    private Path file(String sourceId) {
        Sources.validateId(sourceId);
        return directory.resolve(sourceId + ".txt");
    }
}
