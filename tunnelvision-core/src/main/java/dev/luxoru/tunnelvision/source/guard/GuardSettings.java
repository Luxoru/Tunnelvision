package dev.luxoru.tunnelvision.source.guard;

import java.time.Duration;

public record GuardSettings(double minRetainedRatio, double maxUnsafeRatio, double staleAfterMultiplier,
                            Duration minStaleAfter, Duration retryDelay) {
    public static final GuardSettings DEFAULTS = new GuardSettings(0.5, 0.01, 3, Duration.ofHours(24), Duration.ofMinutes(5));

    public Duration staleAfter(Duration refreshInterval) {
        Duration scaled = Duration.ofMillis((long) (refreshInterval.toMillis() * staleAfterMultiplier));

        if(scaled.compareTo(minStaleAfter) > 0) {
            return scaled;
        }

        return minStaleAfter;
    }
}
