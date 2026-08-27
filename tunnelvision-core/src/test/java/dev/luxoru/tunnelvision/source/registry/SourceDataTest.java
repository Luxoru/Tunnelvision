package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.net.IpBlock;
import dev.luxoru.tunnelvision.source.guard.GuardSettings;
import dev.luxoru.tunnelvision.source.guard.SourceGuard;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDataTest {
    @Test
    void countsAcceptedEntriesBeforeMerging() {
        Instant now = Instant.parse("2026-10-15T00:00:00Z");
        SourceGuard.Result result = SourceGuard.check(List.of("45.1.0.0/16", "45.1.2.0/24", "garbage"), 0,
            GuardSettings.DEFAULTS, true);

        SourceData data = SourceData.of(result, now);
        assertThat(data.entries()).isEqualTo(2); // Both valid entries count...
        assertThat(data.index().size()).isEqualTo(1); // ...but the /24 merges into the /16
        assertThat(data.index().find(IpBlock.parse("45.1.2.3").orElseThrow())).isPresent();
        assertThat(data.refreshedAt()).isEqualTo(now);
    }
}
