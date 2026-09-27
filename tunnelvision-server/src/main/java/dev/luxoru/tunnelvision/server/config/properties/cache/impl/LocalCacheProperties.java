package dev.luxoru.tunnelvision.server.config.properties.cache.impl;

import java.time.Duration;

public record LocalCacheProperties(boolean enabled,
                                   Duration ttl,
                                   long maxSize) {
}
