package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.source.DetectionSource;
import dev.luxoru.tunnelvision.source.SourceContext;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FakeSource implements DetectionSource {
    private final Duration refreshInterval;
    private final AtomicReference<List<String>> entries = new AtomicReference<>(List.of("45.1.2.0/24"));
    private final AtomicInteger fetches = new AtomicInteger();
    private volatile boolean failing;
    private volatile Runnable onFetch = () -> {};

    public FakeSource() {
        this(Duration.ofHours(1));
    }

    public FakeSource(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public void setEntries(List<String> entries) {
        this.entries.set(entries);
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public void onFetch(Runnable onFetch) {
        this.onFetch = onFetch;
    }

    public int fetches() {
        return fetches.get();
    }

    @Override
    public String id() {
        return "fake";
    }

    @Override
    public String name() {
        return "Fake";
    }

    @Override
    public Category category() {
        return Category.VPN;
    }

    @Override
    public Confidence confidence() {
        return Confidence.CONFIRMED;
    }

    @Override
    public Duration refreshInterval() {
        return refreshInterval;
    }

    @Override
    public List<String> fetch(SourceContext context) throws Exception {
        fetches.incrementAndGet();
        onFetch.run();
        if (failing) {
            throw new IOException("upstream down");
        }
        return entries.get();
    }
}
