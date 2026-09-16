package dev.luxoru.tunnelvision.cache;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class OutageLoggerTest {
    @Test
    void tracksOutagesAndRecovery() {
        OutageLogger outages = new OutageLogger(LoggerFactory.getLogger(OutageLoggerTest.class), "Test");
        assertThat(outages.isDown()).isFalse();

        outages.failure("read from", new RuntimeException("first"));
        outages.failure("read from", new RuntimeException("second"));
        assertThat(outages.isDown()).isTrue();

        outages.success();
        assertThat(outages.isDown()).isFalse();
    }
}
