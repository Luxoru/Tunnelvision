package dev.luxoru.tunnelvision.source.guard;

import dev.luxoru.tunnelvision.net.IpBlock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceGuardTest {
    private static List<String> publicAddresses(int count) {
        List<String> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add("45." + (i / 256 % 256) + "." + (i % 256) + ".1");
        }
        return entries;
    }

    @Test
    void acceptsCleanData() {
        SourceGuard.Result result = SourceGuard.check(List.of("1.2.3.4", "5.6.7.0/24", "bad"), 0, GuardSettings.DEFAULTS, false);
        assertThat(result.rejected()).isFalse();
        assertThat(result.accepted()).extracting(IpBlock::toString).containsExactly("1.2.3.4", "5.6.7.0/24");
        assertThat(result.invalid()).isEqualTo(1);
    }

    @Test
    void dropsAFewUnsafeEntries() {
        List<String> entries = publicAddresses(200);
        entries.add("10.0.0.0/8");
        entries.add("2.0.0.0/7"); // Too broad, though not reserved
        SourceGuard.Result result = SourceGuard.check(entries, 0, GuardSettings.DEFAULTS, false);
        assertThat(result.rejected()).isFalse();
        assertThat(result.accepted()).hasSize(200);
        assertThat(result.unsafe()).containsExactly("10.0.0.0/8", "2.0.0.0/7");
    }

    @Test
    void rejectsMostlyUnsafeData() {
        List<String> entries = new ArrayList<>(publicAddresses(10));
        entries.add("0.0.0.0/0");
        assertThat(SourceGuard.check(entries, 0, GuardSettings.DEFAULTS, false).rejected()).isTrue();
    }

    @Test
    void rejectsEmptyData() {
        assertThat(SourceGuard.check(List.of("<html>error</html>"), 0, GuardSettings.DEFAULTS, false).rejected()).isTrue();
    }

    @Test
    void rejectsLargeShrinks() {
        assertThat(SourceGuard.check(publicAddresses(40), 100, GuardSettings.DEFAULTS, false).rejected()).isTrue();
        assertThat(SourceGuard.check(publicAddresses(60), 100, GuardSettings.DEFAULTS, false).rejected()).isFalse();
    }

    @Test
    void trustedDataIsNeverRejected() {
        SourceGuard.Result result = SourceGuard.check(List.of("10.0.0.1", "1.2.3.4"), 100, GuardSettings.DEFAULTS, true);
        assertThat(result.rejected()).isFalse();
        assertThat(result.accepted()).hasSize(1);
        assertThat(result.unsafe()).containsExactly("10.0.0.1");
    }
}
