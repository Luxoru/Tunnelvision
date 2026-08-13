package dev.luxoru.tunnelvision.net;

import org.jspecify.annotations.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An IPv4 or IPv6 CIDR block, or a single address (a /32 or /128 block).
 * <p>
 * Both families share one 128-bit space, with IPv4 stored as IPv4-mapped IPv6 ({@code ::ffff:0:0/96}).
 * So {@code ::ffff:1.2.3.4} and {@code 1.2.3.4} are the same address, and an IPv6 block wide enough to cover
 * {@code ::ffff:0:0/96}, such as {@code ::/0}, contains every IPv4 address.
 * </p>
 */
public final class IpBlock {
    private static final long IPV4_MAPPED_PREFIX = 0x0000FFFF00000000L;
    private static final Pattern IPV4 = Pattern.compile("(0|[1-9]\\d{0,2})(\\.(0|[1-9]\\d{0,2})){3}");
    private static final Pattern PREFIX = Pattern.compile("0|[1-9]\\d{0,2}");

    private final long startHi, startLo, endHi, endLo;
    private final boolean ipv4;
    private final int prefix;
    private final String canonical;

    private IpBlock(InetAddress address, int prefix) {
        this.ipv4 = address instanceof Inet4Address;
        this.prefix = prefix;
        ByteBuffer buffer = ByteBuffer.wrap(address.getAddress());
        long hi, lo;
        if (ipv4) {
            hi = 0L;
            lo = IPV4_MAPPED_PREFIX | (buffer.getInt() & 0xFFFFFFFFL);
        } else {
            hi = buffer.getLong();
            lo = buffer.getLong();
        }
        int hostBits = (ipv4 ? 32 : 128) - prefix;
        long hiMask = hostBits > 64 ? (hostBits == 128 ? -1L : (1L << (hostBits - 64)) - 1) : 0L;
        long loMask = hostBits >= 64 ? -1L : (1L << hostBits) - 1;
        this.startHi = hi & ~hiMask;
        this.startLo = lo & ~loMask;
        this.endHi = startHi | hiMask;
        this.endLo = startLo | loMask;
        this.canonical = format(ipv4, startHi, startLo) + (prefix == (ipv4 ? 32 : 128) ? "" : "/" + prefix);
    }

    public static IpBlock of(InetAddress address) {
        return new IpBlock(address, address instanceof Inet4Address ? 32 : 128);
    }

    public static Optional<IpBlock> parse(@Nullable String text) {
        if (text == null) {
            return Optional.empty();
        }
        text = text.strip();
        int slash = text.indexOf('/');
        String literal = slash < 0 ? text : text.substring(0, slash);
        boolean v6Literal = literal.indexOf(':') >= 0;
        // InetAddress accepts shorthand IPv4 like "10.1" and scoped IPv6 like "fe80::1%eth0", neither belong in a list
        if (literal.isEmpty() || literal.indexOf('%') >= 0 || (!v6Literal && !IPV4.matcher(literal).matches())) {
            return Optional.empty();
        }
        if (v6Literal && literal.indexOf('.') >= 0 && !IPV4.matcher(literal.substring(literal.lastIndexOf(':') + 1)).matches()) {
            return Optional.empty();
        }
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(literal);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        int max = address instanceof Inet4Address ? 32 : 128;
        int prefix = max;
        if (slash >= 0) {
            String digits = text.substring(slash + 1);
            if (!PREFIX.matcher(digits).matches()) {
                return Optional.empty();
            }
            prefix = Integer.parseInt(digits);
            if (v6Literal && address instanceof Inet4Address) {
                // An IPv4-mapped block, e.g. ::ffff:10.0.0.0/104. Anything wider than /96 reaches outside the
                // mapped space, which can't be represented as an IPv4 block, so it's rejected below
                prefix -= 96;
            }
        }
        if (prefix < 0 || prefix > max) {
            return Optional.empty();
        }
        IpBlock block = new IpBlock(address, prefix);
        IpBlock exact = new IpBlock(address, max);
        // Host bits set, e.g. 10.0.0.5/8, is almost always a typo (for /32), so refuse to guess
        if (block.startHi != exact.startHi || block.startLo != exact.startLo) {
            return Optional.empty();
        }
        return Optional.of(block);
    }

    public boolean isIPv4() {
        return ipv4;
    }

    public int prefix() {
        return prefix;
    }

    public boolean isSingleAddress() {
        return prefix == (ipv4 ? 32 : 128);
    }

    public boolean contains(IpBlock other) {
        return compare(startHi, startLo, other.startHi, other.startLo) <= 0
               && compare(endHi, endLo, other.endHi, other.endLo) >= 0;
    }

    public long startHi() {
        return startHi;
    }

    public long startLo() {
        return startLo;
    }

    public long endHi() {
        return endHi;
    }

    public long endLo() {
        return endLo;
    }

    public static int compare(long aHi, long aLo, long bHi, long bLo) {
        int hi = Long.compareUnsigned(aHi, bHi);
        return hi != 0 ? hi : Long.compareUnsigned(aLo, bLo);
    }

    private static String format(boolean ipv4, long hi, long lo) {
        if (ipv4) {
            return (lo >>> 24 & 0xFF) + "." + (lo >>> 16 & 0xFF) + "." + (lo >>> 8 & 0xFF) + "." + (lo & 0xFF);
        }
        int[] groups = new int[8];
        for (int i = 0; i < 4; i++) {
            groups[i] = (int) (hi >>> (48 - 16 * i)) & 0xFFFF;
            groups[i + 4] = (int) (lo >>> (48 - 16 * i)) & 0xFFFF;
        }
        int bestStart = -1, bestLength = 1;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < 8 && groups[i] == 0) {
                i++;
            }
            if (i - start > bestLength) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        StringBuilder builder = new StringBuilder(39);
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                builder.append("::");
                i += bestLength - 1;
                continue;
            }
            if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != ':') {
                builder.append(':');
            }
            builder.append(Integer.toHexString(groups[i]));
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IpBlock block && startHi == block.startHi && startLo == block.startLo
               && endHi == block.endHi && endLo == block.endLo;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(startHi) * 31 * 31 * 31 + Long.hashCode(startLo) * 31 * 31 + Long.hashCode(endHi) * 31 + Long.hashCode(endLo);
    }

    @Override
    public String toString() {
        return canonical;
    }
}
