package dev.luxoru.tunnelvision.net;

import java.util.List;

/**
 * Private, reserved and special-purpose address space (IANA special-purpose registries).
 */
public final class ReservedRanges {
    private static final IpRangeIndex RESERVED = IpRangeIndex.parse(List.of(
        // IPv4
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12",
        "192.0.0.0/24", "192.0.2.0/24", "192.31.196.0/24", "192.52.193.0/24", "192.88.99.0/24", "192.168.0.0/16",
        "192.175.48.0/24", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4",
        // IPv6
        "::/128", "::1/128", "64:ff9b:1::/48", "100::/64", "2001:db8::/32", "3fff::/20", "5f00::/16",
        "fc00::/7", "fe80::/10", "fec0::/10", "ff00::/8"
    ));

    private ReservedRanges() {}

    /**
     * Whether any part of the given block is private or reserved.
     */
    public static boolean isReserved(IpBlock block) {
        return RESERVED.overlaps(block);
    }
}
