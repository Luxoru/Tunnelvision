package dev.luxoru.tunnelvision.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IpBlockTest {
    @ParameterizedTest
    @CsvSource({
        "1.2.3.4, 1.2.3.4",
        "10.0.0.0/8, 10.0.0.0/8",
        "1.2.3.4/32, 1.2.3.4",
        "0.0.0.0/0, 0.0.0.0/0",
        "2001:DB8:0:0:0:0:0:1, 2001:db8::1",
        "2001:db8::/32, 2001:db8::/32",
        "2001:db8:1::/48, 2001:db8:1::/48",
        "::/0, ::/0",
        "2001:db8::/64, 2001:db8::/64",
        "2001:db8::/127, 2001:db8::/127",
        "2001:db8::1/128, 2001:db8::1",
        "0.0.0.0, 0.0.0.0",
        "2001:0:0:1:0:0:0:1, 2001:0:0:1::1", // The longest zero run is compressed
        "::1, ::1",
        "::, ::",
        "1::, 1::",
        "::ffff:1.2.3.4, 1.2.3.4",
        "::ffff:10.0.0.0/104, 10.0.0.0/8",
        "' 8.8.8.8 ', 8.8.8.8",
    })
    void parsesToCanonicalForm(String input, String expected) {
        assertThat(IpBlock.parse(input)).hasValueSatisfying(block -> assertThat(block).hasToString(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "example.com", "1.2.3", "10.1", "1.2.3.256", "1.2.3.4/33", "::1/129", "1.2.3.4/-1",
        "1.2.3.4/abc", "fe80::1%eth0", "::ffff:1.2.3.4/64", "1.2.3.4/",
        // Leading zeros, read as octal by some tools
        "010.0.0.1", "10.0.0.01", "00.0.0.0", "::ffff:010.0.0.1",
        // Loose prefixes that Integer.parseInt would accept
        "1.2.3.4/+8", "1.0.0.0/08", "1.0.0.0/٨",
        // Host bits set, almost always a typo
        "10.0.0.5/8", "10.1.2.3/8", "2001:db8::1/64", "::1/0" })
    void rejectsInvalidInput(String input) {
        assertThat(IpBlock.parse(input)).isEmpty();
    }

    @Test
    void containment() {
        IpBlock block = IpBlock.parse("10.0.0.0/8").orElseThrow();
        assertThat(block.contains(IpBlock.parse("10.255.255.255").orElseThrow())).isTrue();
        assertThat(block.contains(IpBlock.parse("11.0.0.0").orElseThrow())).isFalse();
        assertThat(block.contains(IpBlock.parse("::ffff:10.0.0.1").orElseThrow())).isTrue();
        assertThat(block.contains(IpBlock.parse("10::1").orElseThrow())).isFalse();
    }

    @Test
    void anIpv6BlockCoveringTheMappedSpaceContainsIpv4() {
        IpBlock all = IpBlock.parse("::/0").orElseThrow();
        assertThat(all.contains(IpBlock.parse("8.8.8.8").orElseThrow())).isTrue();
        assertThat(IpBlock.parse("2000::/3").orElseThrow().contains(IpBlock.parse("8.8.8.8").orElseThrow())).isFalse();
    }

    @Test
    void equality() {
        assertThat(IpBlock.parse("::ffff:10.0.0.0/104")).isEqualTo(IpBlock.parse("10.0.0.0/8"));
        assertThat(IpBlock.parse("10.0.0.0/8")).isNotEqualTo(IpBlock.parse("10.0.0.0/9"));
    }
}
