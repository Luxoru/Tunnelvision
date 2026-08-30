package dev.luxoru.tunnelvision;

import dev.luxoru.tunnelvision.cache.VerdictCache;
import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.detection.Verdict;
import dev.luxoru.tunnelvision.source.list.StaticSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TunnelVisionTest {
    private final Map<String, Verdict> cached = new ConcurrentHashMap<>();
    private TunnelVision tunnelVision;

    @BeforeEach
    void setUp() {
        VerdictCache cache = new VerdictCache() {

            @Override
            public Optional<Verdict> get(String ip) {
                return Optional.ofNullable(cached.get(ip));
            }

            @Override
            public void put(String ip, Verdict verdict) {
                cached.put(ip, verdict);
            }

            @Override
            public void clear() {
                cached.clear();
            }

        };
        tunnelVision = TunnelVision.builder()
            .sources(List.of(
                new StaticSource("vpn", "Some VPN", Category.VPN, Confidence.CONFIRMED, List.of("45.1.0.0/16", "2a00:1::/32")),
                new StaticSource("cloud", "Some Cloud", Category.HOSTING, Confidence.CONFIRMED, List.of("45.0.0.0/8"))))
            .allowlist(List.of("45.1.2.0/24"))
            .cache(cache)
            .refreshEnabled(false)
            .build();
        tunnelVision.start();
    }

    @AfterEach
    void tearDown() {
        tunnelVision.close();
    }

    @Test
    void checksAddresses() throws Exception {
        Verdict verdict = tunnelVision.check("45.1.9.9");
        assertThat(verdict.blocked()).isTrue();
        assertThat(verdict.provider()).isEqualTo("Some VPN");
        assertThat(verdict.is(Category.HOSTING)).isTrue();
        assertThat(verdict.detections()).hasSize(2);

        assertThat(tunnelVision.check("2a00:1:0::5").is(Category.VPN)).isTrue();
        assertThat(tunnelVision.check(InetAddress.getByName("45.200.0.1")).blocked()).isFalse();
        assertThat(tunnelVision.check("8.8.8.8").detections()).isEmpty();
        assertThat(tunnelVision.isReady()).isTrue();
    }

    @Test
    void reservedAndAllowlistedAreClean() {
        assertThat(tunnelVision.check("192.168.1.1").status()).isEqualTo(Verdict.Status.RESERVED);
        Verdict allowlisted = tunnelVision.check("45.1.2.3");
        assertThat(allowlisted.status()).isEqualTo(Verdict.Status.ALLOWLISTED);
        assertThat(allowlisted.blocked()).isFalse();
    }

    @Test
    void cachesByCanonicalAddress() {
        tunnelVision.check("::ffff:45.1.9.9");
        assertThat(cached).containsKey("45.1.9.9");
        tunnelVision.refresh("vpn");
        assertThat(cached).isEmpty(); // Cleared when data changes
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> tunnelVision.check("example.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tunnelVision.check("45.0.0.0/8")).isInstanceOf(IllegalArgumentException.class);
    }
}
