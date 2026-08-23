package dev.luxoru.tunnelvision.source.list;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.source.DetectionSource;
import dev.luxoru.tunnelvision.source.SourceContext;
import dev.luxoru.tunnelvision.source.Sources;

import java.time.Duration;
import java.util.List;

/**
 * A fixed list of entries maintained by the operator, such as a denylist.
 */
public record StaticSource(String id, String name, Category category, Confidence confidence,
                           List<String> entries) implements DetectionSource {

    @Override
    public Duration refreshInterval() {
        return Duration.ZERO;
    }

    @Override
    public List<String> fetch(SourceContext context) {
        return entries;
    }

    @Override
    public boolean trusted() {
        return true;
    }
}
