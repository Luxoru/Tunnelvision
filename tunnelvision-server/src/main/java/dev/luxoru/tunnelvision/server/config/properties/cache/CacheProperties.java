package dev.luxoru.tunnelvision.server.config.properties.cache;

import dev.luxoru.tunnelvision.server.config.properties.cache.impl.CaffeineProperties;

import java.time.Duration;

public record CacheProperties(CacheType type, Duration ttl,
                              CaffeineProperties caffeine) {
}
