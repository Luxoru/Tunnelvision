package dev.luxoru.tunnelvision.net;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedRangesTest {
    @ParameterizedTest
    @ValueSource(strings = { "10.0.0.1", "192.168.1.1", "127.0.0.1", "100.64.0.1", "169.254.1.1", "0.0.0.0",
        "255.255.255.255", "::1", "::", "fe80::1", "fd00::1", "2001:db8::1", "::ffff:192.168.0.1", "0.0.0.0/4" })
    void reserved(String ip) {
        assertThat(ReservedRanges.isReserved(IpBlock.parse(ip).orElseThrow())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.1.1.1", "8.8.8.8", "100.128.0.1", "2606:4700::1111", "11.0.0.0/8" })
    void public_(String ip) {
        assertThat(ReservedRanges.isReserved(IpBlock.parse(ip).orElseThrow())).isFalse();
    }
}
