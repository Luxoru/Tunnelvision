package dev.luxoru.tunnelvision.cache;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import dev.luxoru.tunnelvision.detection.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


public final class HazelcastVerdictCache implements VerdictCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastVerdictCache.class);

    private final IMap<String, byte[]> map;
    private final Duration ttl;
    private final OutageLogger outages = new OutageLogger(LOGGER, "Hazelcast");

    public HazelcastVerdictCache(HazelcastInstance hazelcast, String mapName, Duration ttl) {
        this.map = hazelcast.getMap(mapName);
        this.ttl = ttl;
    }

    @Override
    public Optional<Verdict> get(String ip) {
        try {
            byte[] value = map.get(ip);
            outages.success();
            return value != null ? VerdictCodec.decode(value) : Optional.empty();
        } catch (RuntimeException ex) {
            outages.failure("read from", ex);
            return Optional.empty();
        }
    }

    @Override
    public void put(String ip, Verdict verdict) {
        try {
            map.set(ip, VerdictCodec.encode(verdict), ttl.toMillis(), TimeUnit.MILLISECONDS);
            outages.success();
        } catch (RuntimeException ex) {
            outages.failure("write to", ex);
        }
    }

    @Override
    public void clear() {
        try {
            map.clear();
            outages.success();
        } catch (RuntimeException ex) {
            outages.failure("clear", ex);
        }
    }
}
