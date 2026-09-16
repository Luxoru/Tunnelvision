package dev.luxoru.tunnelvision.cache;

import dev.luxoru.tunnelvision.detection.Verdict;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link VerdictCache} shared through Redis, or anything that speaks its protocol (Valkey, Dragonfly, KeyDB).
 * TODO:
 *  - Support Redis Cluster, and horizontally scaled redis protocol services
 */
public final class RedisVerdictCache implements VerdictCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisVerdictCache.class);
    private static final RedisCodec<String, byte[]> CODEC = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
    private static final String GLOB_META = "*?[]\\";

    private final StatefulRedisConnection<String, byte[]> connection;
    private final RedisCommands<String, byte[]> commands;
    private final String prefix;
    private final Duration ttl;
    private final @Nullable RedisClient ownedClient;
    private final OutageLogger outages = new OutageLogger(LOGGER, "Redis");

    public RedisVerdictCache(String uri, String prefix, Duration ttl) {
        this(RedisClient.create(uri), true, prefix, ttl);
    }

    public RedisVerdictCache(RedisClient client, String prefix, Duration ttl) {
        this(client, false, prefix, ttl);
    }

    private RedisVerdictCache(RedisClient client, boolean owned, String prefix, Duration ttl) {
        try {
            validate(prefix, ttl);
            this.connection = client.connect(CODEC);
        } catch (RuntimeException ex) {
            if (owned) {
                client.shutdown();
            }
            throw ex;
        }
        this.commands = connection.sync();
        this.prefix = prefix;
        this.ttl = ttl;
        this.ownedClient = owned ? client : null;
    }

    private static void validate(String prefix, Duration ttl) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(ttl, "ttl");
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("Redis prefix must not be empty"); // Don't wanna wipe the whole DB :)
        }
        if (prefix.chars().anyMatch(c -> GLOB_META.indexOf(c) >= 0)) {
            throw new IllegalArgumentException("Redis prefix can't contain * ? [ ] or \\: " + prefix);
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive: " + ttl);
        }
    }

    @Override
    public Optional<Verdict> get(String ip) {
        try {
            byte[] value = commands.get(prefix + ip);
            outages.success();

            if(value != null) {
                return VerdictCodec.decode(value);
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            outages.failure("read from", ex);
            return Optional.empty();
        }
    }

    @Override
    public void put(String ip, Verdict verdict) {
        try {
            commands.set(prefix + ip, VerdictCodec.encode(verdict), SetArgs.Builder.px(ttl));
            outages.success();
        } catch (RuntimeException ex) {
            outages.failure("write to", ex);
        }
    }

    @Override
    public void clear() {
        try {
            ScanArgs args = ScanArgs.Builder.matches(prefix + "*").limit(1000);
            ScanCursor cursor = ScanCursor.INITIAL;
            do {
                KeyScanCursor<String> page = commands.scan(cursor, args);
                if (!page.getKeys().isEmpty()) {
                    commands.unlink(page.getKeys().toArray(String[]::new));
                }
                cursor = page;
            } while (!cursor.isFinished());
            outages.success();
        } catch (RuntimeException ex) {
            outages.failure("clear", ex);
        }
    }

    @Override
    public void close() {
        connection.close();
        if (ownedClient != null) {
            ownedClient.shutdown();
        }
    }
}