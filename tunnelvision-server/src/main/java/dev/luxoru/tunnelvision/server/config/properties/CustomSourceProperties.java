package dev.luxoru.tunnelvision.server.config.properties;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

public record CustomSourceProperties(String id, @Nullable String name, Category category,
                                     @DefaultValue("LIKELY") Confidence confidence,
                                     @DefaultValue List<String> urls,
                                     @DefaultValue List<String> entries,
                                     @DefaultValue("24h") Duration refreshInterval) {
}
