package dev.luxoru.tunnelvision.server.config.properties.cache;

public enum CacheType {
    NONE, CAFFEINE, REDIS, HAZELCAST;

    public boolean isRemote() {
        return this == REDIS || this == HAZELCAST;
    }
}
