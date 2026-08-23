package dev.luxoru.tunnelvision.source;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.source.list.PlainListSource;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

public final class Sources {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");
    private static final String X4B = "https://raw.githubusercontent.com/X4BNet/lists_vpn/main/output/";

    private Sources() {}

    public static DetectionSource tor() {
        return new PlainListSource("tor", "Tor", Category.TOR, Confidence.CONFIRMED, Duration.ofHours(1),
            List.of("https://check.torproject.org/torbulkexitlist"));
    }

    /**
     * VPN ranges from the community-maintained X4BNet list.
     *
     * @return the source
     */
    public static DetectionSource x4bVpn() {
        return new PlainListSource("x4b-vpn", "X4BNet VPN list", Category.VPN, Confidence.LIKELY, Duration.ofHours(24),
            List.of(X4B + "vpn/ipv4.txt", X4B + "vpn/ipv6.txt"));
    }

    /**
     * Datacenter ranges from the community-maintained X4BNet list.
     *
     * @return the source
     */
    public static DetectionSource x4bDatacenter() {
        return new PlainListSource("x4b-datacenter", "X4BNet datacenter list", Category.HOSTING, Confidence.LIKELY,
            Duration.ofHours(24), List.of(X4B + "datacenter/ipv4.txt", X4B + "datacenter/ipv6.txt"));
    }

    public static List<DetectionSource> builtin() {
        return List.of(tor(), x4bVpn(), x4bDatacenter());
    }

    public static void validateId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid source id \"" + id + "\", use lowercase letters, digits and dashes");
        }
    }
}
