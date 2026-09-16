package dev.luxoru.tunnelvision.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisVerdictCacheTest {
    private static final String URI = "redis://localhost:6379";

    @Test
    void rejectsWildcardsInThePrefix() {
        assertThatThrownBy(() -> new RedisVerdictCache(URI, "tv*", Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tv*");
    }

    @Test
    void rejectsAnEmptyPrefix() {
        assertThatThrownBy(() -> new RedisVerdictCache(URI, "", Duration.ofMinutes(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void rejectsAZeroOrNegativeTtl() {
        assertThatThrownBy(() -> new RedisVerdictCache(URI, "tv:", Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisVerdictCache(URI, "tv:", Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
