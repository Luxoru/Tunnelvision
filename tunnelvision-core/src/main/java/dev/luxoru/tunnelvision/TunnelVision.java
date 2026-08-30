package dev.luxoru.tunnelvision;

import dev.luxoru.tunnelvision.cache.VerdictCache;
import dev.luxoru.tunnelvision.detection.BlockPolicy;
import dev.luxoru.tunnelvision.detection.Verdict;
import dev.luxoru.tunnelvision.detection.VerdictEngine;
import dev.luxoru.tunnelvision.detection.Weights;
import dev.luxoru.tunnelvision.net.IpBlock;
import dev.luxoru.tunnelvision.net.IpRangeIndex;
import dev.luxoru.tunnelvision.net.ReservedRanges;
import dev.luxoru.tunnelvision.source.DetectionSource;
import dev.luxoru.tunnelvision.source.SourceContext;
import dev.luxoru.tunnelvision.source.Sources;
import dev.luxoru.tunnelvision.source.guard.GuardSettings;
import dev.luxoru.tunnelvision.source.registry.SourceRegistry;
import dev.luxoru.tunnelvision.source.registry.SourceStatus;
import dev.luxoru.tunnelvision.source.snapshot.FileSnapshotStore;
import dev.luxoru.tunnelvision.source.snapshot.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Tells you whether an address is a VPN, Tor exit, proxy, datacenter or known-abusive network.
 * <p>
 * Embed it directly in any JVM application, such as a proxy deciding whether to let a
 * connection through:
 * </p>
 * <pre>{@code
 * TunnelVision tunnelVision = TunnelVision.builder()
 *     .snapshotStore(new FileSnapshotStore(Path.of("data/sources")))
 *     .cache(new CaffeineVerdictCache(Duration.ofMinutes(10), 100_000))
 *     .build();
 * tunnelVision.start();
 *
 * if (tunnelVision.check(address).blocked()) {
 *     connection.close();
 * }
 * }</pre>
 * Lookups are in-memory and safe to call from any thread.
 */
public final class TunnelVision implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TunnelVision.class);

    private final SourceRegistry sources;
    private final VerdictEngine engine;
    private final VerdictCache cache;
    private final IpRangeIndex allowlist;
    private final boolean refreshEnabled;

    private TunnelVision(Builder builder) {
        this.cache = builder.cache;
        this.engine = new VerdictEngine(builder.weights, builder.blockPolicy);
        this.allowlist = IpRangeIndex.parse(builder.allowlist);
        this.refreshEnabled = builder.refreshEnabled;
        this.sources = new SourceRegistry(builder.sources, builder.context, builder.snapshotStore, builder.guard,
            builder.clock, this::onSourceChange);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void start() {
        sources.load();
        if (refreshEnabled) {
            sources.start();
        }
    }

    /**
     * Check an address.
     *
     * @param ip an IPv4 or IPv6 address literal, never a hostname
     * @return the verdict
     * @throws IllegalArgumentException if it isn't a valid address
     */
    public Verdict check(String ip) {
        Optional<IpBlock> address = IpBlock.parse(ip).filter(IpBlock::isSingleAddress);
        if (address.isEmpty()) {
            throw new IllegalArgumentException("Not a valid IP address: " + ip);
        }
        return check(address.get());
    }

    /**
     * Check an address.
     *
     * @param address the address
     * @return the verdict
     */
    public Verdict check(InetAddress address) {
        return check(IpBlock.of(address));
    }

    private Verdict check(IpBlock address) {
        String ip = address.toString();
        if (ReservedRanges.isReserved(address)) {
            return Verdict.reserved(ip);
        }
        if (allowlist.contains(address)) {
            return Verdict.allowlisted(ip);
        }
        Optional<Verdict> cached = cache.get(ip);
        if (cached.isPresent()) {
            return cached.get();
        }
        Verdict verdict = engine.evaluate(ip, sources.lookup(address));
        cache.put(ip, verdict);
        return verdict;
    }

    /**
     * The state of every source.
     *
     * @return the states
     */
    public List<SourceStatus> sources() {
        return sources.status();
    }

    /**
     * Whether every source has loaded its data, or at least tried to.
     *
     * @return true if ready
     */
    public boolean isReady() {
        return sources.initialized();
    }

    /**
     * Refresh a source now, rather than waiting for its schedule.
     *
     * @param sourceId the source id
     * @return true if the refresh was accepted
     * @throws IllegalArgumentException if there's no such source
     */
    public boolean refresh(String sourceId) {
        return sources.refresh(sourceId);
    }

    private void onSourceChange() {
        // Never serve a verdict computed from older data than a fresh lookup would use
        try {
            cache.clear();
        } catch (Exception ex) {
            LOGGER.warn("Couldn't clear the verdict cache", ex);
        }
    }

    @Override
    public void close() {
        try (cache) {
            sources.close();
        }
    }

    public static final class Builder {
        private final List<DetectionSource> sources = new ArrayList<>(Sources.builtin());
        private final List<String> allowlist = new ArrayList<>();
        private VerdictCache cache = VerdictCache.none();
        private SnapshotStore snapshotStore = SnapshotStore.none();
        private SourceContext context = SourceContext.defaults();
        private Weights weights = Weights.DEFAULTS;
        private BlockPolicy blockPolicy = BlockPolicy.DEFAULT;
        private GuardSettings guard = GuardSettings.DEFAULTS;
        private Clock clock = Clock.systemUTC();
        private boolean refreshEnabled = true;

        private Builder() {}

        /**
         * Replace the sources, the {@link Sources#builtin() built-in sources} by default.
         */
        public Builder sources(Collection<? extends DetectionSource> sources) {
            this.sources.clear();
            this.sources.addAll(sources);
            return this;
        }

        /**
         * Add a source on top of the current ones.
         */
        public Builder addSource(DetectionSource source) {
            sources.add(source);
            return this;
        }

        /**
         * Addresses and CIDR blocks that are always clean.
         */
        public Builder allowlist(Collection<String> entries) {
            for (String entry : entries) {
                if (IpBlock.parse(entry).isEmpty()) {
                    throw new IllegalArgumentException("Invalid allowlist entry: " + entry);
                }
            }
            allowlist.addAll(entries);
            return this;
        }

        public Builder cache(VerdictCache cache) {
            this.cache = cache;
            return this;
        }

        public Builder snapshotStore(SnapshotStore snapshotStore) {
            this.snapshotStore = snapshotStore;
            return this;
        }

        public Builder sourceContext(SourceContext context) {
            this.context = context;
            return this;
        }

        public Builder weights(Weights weights) {
            this.weights = weights;
            return this;
        }

        public Builder blockPolicy(BlockPolicy blockPolicy) {
            this.blockPolicy = blockPolicy;
            return this;
        }

        public Builder guard(GuardSettings guard) {
            this.guard = guard;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Whether sources are refreshed in the background, true by default. Without it only
         * snapshots and static sources are loaded, refresh them yourself with {@link #refresh(String)}.
         */
        public Builder refreshEnabled(boolean refreshEnabled) {
            this.refreshEnabled = refreshEnabled;
            return this;
        }

        public TunnelVision build() {
            return new TunnelVision(this);
        }
    }
}
