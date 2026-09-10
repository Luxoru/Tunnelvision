package dev.luxoru.tunnelvision.server.config.properties;

import dev.luxoru.tunnelvision.server.config.properties.cache.CacheProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties("tunnelvision")
public record TunnelVisionProperties(
    boolean refreshEnabled,
    @Nullable Path snapshotDirectory,
    List<String> allowlist,
    SourcesProperties sources,
    PolicyProperties policy,
    WeightsProperties weights,
    GuardProperties guard,
    CacheProperties cache
) {
}
