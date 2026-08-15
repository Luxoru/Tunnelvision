package dev.luxoru.tunnelvision.net;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IpRangeIndexTest {
    private static IpBlock ip(String text) {
        return IpBlock.parse(text).orElseThrow();
    }

    @Test
    void findsTheContainingBlock() {
        IpRangeIndex index = IpRangeIndex.parse(List.of("10.0.0.0/8", "192.0.2.5", "2001:db8::/32", "garbage"));
        assertThat(index.find(ip("10.20.30.40"))).hasValue(ip("10.0.0.0/8"));
        assertThat(index.find(ip("192.0.2.5"))).hasValue(ip("192.0.2.5"));
        assertThat(index.find(ip("2001:db8:ffff::1"))).hasValue(ip("2001:db8::/32"));
        assertThat(index.find(ip("192.0.2.4"))).isEmpty();
        assertThat(index.find(ip("192.0.2.6"))).isEmpty();
        assertThat(index.find(ip("2001:db9::1"))).isEmpty();
        assertThat(index.find(ip("11.0.0.0"))).isEmpty();
    }

    @Test
    void dropsNestedBlocks() {
        IpRangeIndex index = IpRangeIndex.parse(List.of("10.1.0.0/16", "10.0.0.0/8", "10.1.2.3", "10.0.0.0/8"));
        assertThat(index.blocks()).containsExactly(ip("10.0.0.0/8"));
    }

    @Test
    void ipv4AndIpv6DoNotCollide() {
        // IPv4 lives in ::ffff:0:0/96, so the old IPv4-compatible block ::/96 must not match it
        IpRangeIndex index = IpRangeIndex.parse(List.of("::/96"));
        assertThat(index.contains(ip("1.2.3.4"))).isFalse();
        assertThat(index.contains(ip("::1.2.3.4"))).isTrue();
    }

    @Test
    void overlaps() {
        IpRangeIndex index = IpRangeIndex.parse(List.of("10.1.0.0/16"));
        assertThat(index.overlaps(ip("10.0.0.0/8"))).isTrue();
        assertThat(index.overlaps(ip("10.1.2.0/24"))).isTrue();
        assertThat(index.overlaps(ip("10.2.0.0/16"))).isFalse();
    }

    @Test
    void emptyIndex() {
        assertThat(IpRangeIndex.empty().contains(ip("1.1.1.1"))).isFalse();
        assertThat(IpRangeIndex.parse(List.of()).size()).isZero();
    }
}
