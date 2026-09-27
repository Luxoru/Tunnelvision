package dev.luxoru.tunnelvision.server.config.properties.cache;

import dev.luxoru.tunnelvision.server.config.properties.cache.impl.CaffeineProperties;
import dev.luxoru.tunnelvision.server.config.properties.cache.impl.HazelcastProperties;
import dev.luxoru.tunnelvision.server.config.properties.cache.impl.LocalCacheProperties;
import dev.luxoru.tunnelvision.server.config.properties.cache.impl.RedisProperties;

import java.time.Duration;

public record CacheProperties(CacheType type, Duration ttl,
                              LocalCacheProperties local, CaffeineProperties caffeine,
                              RedisProperties redis, HazelcastProperties hazelcast) {
}
