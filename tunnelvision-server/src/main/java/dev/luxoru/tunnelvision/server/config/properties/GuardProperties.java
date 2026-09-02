package dev.luxoru.tunnelvision.server.config.properties;

import java.time.Duration;

public record GuardProperties(double minRetainedRatio,
                              double maxUnsafeRatio,
                              double staleAfterMultiplier,
                              Duration minStaleAfter,
                              Duration retryDelay) {
}
