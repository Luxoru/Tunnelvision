package dev.luxoru.tunnelvision.cache;

import dev.luxoru.tunnelvision.detection.Verdict;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineVerdictCacheTest {
    @Test
    void storesAndClears() {
        CaffeineVerdictCache cache = new CaffeineVerdictCache(Duration.ofMinutes(1), 100);
        Verdict verdict = Verdict.reserved("10.0.0.1");
        cache.put("10.0.0.1", verdict);
        assertThat(cache.get("10.0.0.1")).hasValue(verdict);
        cache.clear();
        assertThat(cache.get("10.0.0.1")).isEmpty();
    }

    @Test
    void storesAndClearsWithAutoCloseable() {
        try (var cache = new CaffeineVerdictCache(Duration.ofMinutes(1), 100)) {
            Verdict verdict = Verdict.reserved("10.0.0.1");
            cache.put("10.0.0.1", verdict);
            assertThat(cache.get("10.0.0.1")).hasValue(verdict);
            cache.clear();
            assertThat(cache.get("10.0.0.1")).isEmpty();
        }
    }

    @Test
    void tieredFillsTheLocalCache() {
        CaffeineVerdictCache local = new CaffeineVerdictCache(Duration.ofMinutes(1), 100);
        CaffeineVerdictCache shared = new CaffeineVerdictCache(Duration.ofMinutes(1), 100);
        Verdict verdict = Verdict.reserved("10.0.0.1");
        shared.put("10.0.0.1", verdict);
        TieredVerdictCache tiered = new TieredVerdictCache(local, shared);
        assertThat(tiered.get("10.0.0.1")).hasValue(verdict);
        assertThat(local.get("10.0.0.1")).hasValue(verdict);
        tiered.clear();
        assertThat(shared.get("10.0.0.1")).isEmpty();
    }
}
