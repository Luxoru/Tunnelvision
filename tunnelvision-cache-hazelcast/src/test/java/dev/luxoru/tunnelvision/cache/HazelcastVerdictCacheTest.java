package dev.luxoru.tunnelvision.cache;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import dev.luxoru.tunnelvision.detection.Verdict;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HazelcastVerdictCacheTest {
    private static HazelcastInstance hazelcast;

    @BeforeAll
    static void start() {
        Config config = new Config();
        config.setClusterName("test-" + UUID.randomUUID());
        config.setProperty("hazelcast.phone.home.enabled", "false");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        hazelcast = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    static void stop() {
        hazelcast.shutdown();
    }

    @Test
    void storesAndClears() {
        HazelcastVerdictCache cache = new HazelcastVerdictCache(hazelcast, "verdicts", Duration.ofMinutes(1));
        Verdict verdict = Verdict.allowlisted("1.2.3.4");
        cache.put("1.2.3.4", verdict);
        assertThat(cache.get("1.2.3.4")).hasValue(verdict);
        cache.clear();
        assertThat(cache.get("1.2.3.4")).isEmpty();
    }

    @Test
    void getReturnsEmptyWhenHazelcastFails() {
        IMap<String, byte[]> map = mock();
        when(map.get("1.2.3.4")).thenThrow(new RuntimeException("cluster unreachable"));
        HazelcastInstance failing = mock();
        when(failing.<String, byte[]>getMap("verdicts")).thenReturn(map);

        HazelcastVerdictCache cache = new HazelcastVerdictCache(failing, "verdicts", Duration.ofMinutes(1));
        assertThat(cache.get("1.2.3.4")).isEmpty();
    }
}
