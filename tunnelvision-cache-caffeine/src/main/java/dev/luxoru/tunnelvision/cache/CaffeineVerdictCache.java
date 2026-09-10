package dev.luxoru.tunnelvision.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.luxoru.tunnelvision.detection.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;


public final class CaffeineVerdictCache implements VerdictCache {

    private final Cache<String, Verdict> cache;
    private final Logger LOG = LoggerFactory.getLogger(CaffeineVerdictCache.class);

    public CaffeineVerdictCache(Duration ttl, long maxSize) {
        this(Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build());
    }

    public CaffeineVerdictCache(Cache<String, Verdict> cache) {
        this.cache = cache;
    }

    @Override
    public Optional<Verdict> get(String ip) {
        return Optional.ofNullable(cache.getIfPresent(ip));
    }

    @Override
    public void put(String ip, Verdict verdict) {
        cache.put(ip, verdict);
        LOG.debug("{} -> {}", ip, verdict);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        LOG.info("Caffeine cache cleared");
    }

}
