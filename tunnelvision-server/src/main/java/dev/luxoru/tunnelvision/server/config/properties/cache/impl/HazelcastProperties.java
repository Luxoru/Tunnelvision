package dev.luxoru.tunnelvision.server.config.properties.cache.impl;

public record HazelcastProperties(HazelcastMode mode,
                                  String mapName) {
}
