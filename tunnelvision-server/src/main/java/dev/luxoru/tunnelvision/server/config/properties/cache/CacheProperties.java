package dev.luxoru.tunnelvision.server.config.properties.cache;

import dev.luxoru.tunnelvision.server.config.properties.cache.impl.CaffeineProperties;
import dev.luxoru.tunnelvision.server.config.properties.cache.impl.RedisProperties;

import java.time.Duration;

public record CacheProperties(CacheType type, Duration ttl,
                              CaffeineProperties caffeine, RedisProperties redis) {
}
