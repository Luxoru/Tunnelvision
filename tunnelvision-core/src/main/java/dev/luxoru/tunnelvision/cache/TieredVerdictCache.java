package dev.luxoru.tunnelvision.cache;

import dev.luxoru.tunnelvision.detection.Verdict;

import java.util.Optional;

public final class TieredVerdictCache implements VerdictCache {
    private final VerdictCache local;
    private final VerdictCache shared;

    public TieredVerdictCache(VerdictCache local, VerdictCache shared) {
        this.local = local;
        this.shared = shared;
    }

    @Override
    public Optional<Verdict> get(String ip) {
        Optional<Verdict> verdict = local.get(ip);
        if (verdict.isEmpty()) {
            verdict = shared.get(ip);
            verdict.ifPresent(found -> local.put(ip, found));
        }
        return verdict;
    }

    @Override
    public void put(String ip, Verdict verdict) {
        local.put(ip, verdict);
        shared.put(ip, verdict);
    }

    @Override
    public void clear() {
        local.clear();
        shared.clear();
    }

    @Override
    public void close() {
        try (local; shared) {

        }
    }
}
