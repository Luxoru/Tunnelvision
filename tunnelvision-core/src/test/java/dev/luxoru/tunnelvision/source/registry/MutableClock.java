package dev.luxoru.tunnelvision.source.registry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * I love mutable clocks
 */
public final class MutableClock extends Clock {
    private Instant now = Instant.now();

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
