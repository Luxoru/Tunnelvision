package dev.luxoru.tunnelvision.server.config;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import dev.luxoru.tunnelvision.TunnelVision;
import dev.luxoru.tunnelvision.cache.CaffeineVerdictCache;
import dev.luxoru.tunnelvision.cache.HazelcastVerdictCache;
import dev.luxoru.tunnelvision.cache.RedisVerdictCache;
import dev.luxoru.tunnelvision.cache.VerdictCache;
import dev.luxoru.tunnelvision.detection.BlockPolicy;
import dev.luxoru.tunnelvision.detection.Weights;
import dev.luxoru.tunnelvision.server.config.properties.cache.CacheProperties;
import dev.luxoru.tunnelvision.server.config.properties.CustomSourceProperties;
import dev.luxoru.tunnelvision.server.config.properties.cache.impl.HazelcastMode;
import dev.luxoru.tunnelvision.server.config.properties.TunnelVisionProperties;
import dev.luxoru.tunnelvision.source.DetectionSource;
import dev.luxoru.tunnelvision.source.Sources;
import dev.luxoru.tunnelvision.source.guard.GuardSettings;
import dev.luxoru.tunnelvision.source.list.PlainListSource;
import dev.luxoru.tunnelvision.source.list.StaticSource;
import dev.luxoru.tunnelvision.source.snapshot.FileSnapshotStore;
import dev.luxoru.tunnelvision.source.snapshot.SnapshotStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class TunnelVisionConfiguration {

    @Bean(initMethod = "start", destroyMethod = "close")
    public TunnelVision tunnelVision(TunnelVisionProperties properties, VerdictCache cache,
                                     ObjectProvider<DetectionSource> extraSources) {
        final var weights = properties.weights();
        final var guard = properties.guard();

        final var snapshotStore = properties.snapshotDirectory() == null
                ? SnapshotStore.none()
                : new FileSnapshotStore(properties.snapshotDirectory());

        final var guardSettings = new GuardSettings(guard.minRetainedRatio(),
                guard.maxUnsafeRatio(),
                guard.staleAfterMultiplier(),
                guard.minStaleAfter(),
                guard.retryDelay());

        return TunnelVision.builder()
            .sources(sources(properties, extraSources.orderedStream().toList()))
            .allowlist(properties.allowlist())
            .cache(cache)
            .snapshotStore(snapshotStore)
            .weights(Weights.DEFAULTS.with(weights.confirmed(), weights.likely()))
            .blockPolicy(new BlockPolicy(properties.policy().blockCategories(), properties.policy().riskThreshold()))
            .guard(guardSettings)
            .refreshEnabled(properties.refreshEnabled())
            .build();
    }

    private static List<DetectionSource> sources(TunnelVisionProperties properties, List<DetectionSource> beans) {
        Set<String> disabled = Set.copyOf(properties.sources().disabled());
        List<DetectionSource> sources = new ArrayList<>();
        for (DetectionSource source : Sources.builtin()) {
            if (!disabled.contains(source.id())) {
                sources.add(source);
            }
        }
        for (CustomSourceProperties custom : properties.sources().custom()) {
            String name = custom.name() == null ? custom.id() : custom.name();
            if (custom.urls().isEmpty() == custom.entries().isEmpty()) {
                throw new IllegalArgumentException("Source " + custom.id() + " needs either urls or entries");
            }
            sources.add(custom.urls().isEmpty()
                ? new StaticSource(custom.id(), name, custom.category(), custom.confidence(), custom.entries())
                : new PlainListSource(custom.id(), name, custom.category(), custom.confidence(), custom.refreshInterval(), custom.urls()));
        }
        sources.addAll(beans);
        return sources;
    }

    @Bean
    public VerdictCache verdictCache(TunnelVisionProperties properties, ObjectProvider<HazelcastInstance> hazelcast) {
        CacheProperties cache = properties.cache();
        return switch (cache.type()) {
            case NONE -> VerdictCache.none();
            case CAFFEINE -> new CaffeineVerdictCache(cache.ttl(), cache.caffeine().maxSize());
            case REDIS -> new RedisVerdictCache(cache.redis().uri(), cache.redis().prefix(), cache.ttl());
            case HAZELCAST -> new HazelcastVerdictCache(hazelcast.getObject(), cache.hazelcast().mapName(), cache.ttl());
        };
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "tunnelvision.cache.type", havingValue = "hazelcast")
    public HazelcastInstance hazelcastInstance(TunnelVisionProperties properties) {
        return properties.cache().hazelcast().mode() == HazelcastMode.CLIENT
            ? HazelcastClient.newHazelcastClient(ClientConfig.load())
            : Hazelcast.newHazelcastInstance(Config.load());
    }
}
