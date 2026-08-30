package dev.luxoru.tunnelvision.cache;

import dev.luxoru.tunnelvision.detection.Verdict;

import java.util.Optional;

public enum NoopVerdictCache implements VerdictCache {
    INSTANCE;

    @Override
    public Optional<Verdict> get(String ip) {
        return Optional.empty();
    }

    @Override
    public void put(String ip, Verdict verdict) {}

    @Override
    public void clear() {}
}
