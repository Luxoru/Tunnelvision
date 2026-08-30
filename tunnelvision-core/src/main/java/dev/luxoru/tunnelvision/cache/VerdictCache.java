package dev.luxoru.tunnelvision.cache;

import dev.luxoru.tunnelvision.detection.Verdict;

import java.util.Optional;

public interface VerdictCache extends AutoCloseable {

    Optional<Verdict> get(String ip);

    void put(String ip, Verdict verdict);

    void clear();

    @Override
    default void close() {
    }

    static VerdictCache none() {
        return NoopVerdictCache.INSTANCE;
    }
}
