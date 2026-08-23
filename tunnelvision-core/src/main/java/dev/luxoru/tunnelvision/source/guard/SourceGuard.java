package dev.luxoru.tunnelvision.source.guard;

import dev.luxoru.tunnelvision.net.IpBlock;
import dev.luxoru.tunnelvision.net.ReservedRanges;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks every refresh before it can replace a source's data.
 */
public final class SourceGuard {

    public static final int MIN_IPV4_PREFIX = 8;
    public static final int MIN_IPV6_PREFIX = 24;

    private SourceGuard() {}

    public static Result check(List<String> entries, int previousCount, GuardSettings settings, boolean trusted) {
        List<IpBlock> accepted = new ArrayList<>(entries.size());
        List<String> unsafe = new ArrayList<>();
        int invalid = 0;
        for (String entry : entries) {
            Optional<IpBlock> parsed = IpBlock.parse(entry);
            if (parsed.isEmpty()) {
                invalid++;
            } else if (isUnsafe(parsed.get())) {
                unsafe.add(parsed.get().toString());
            } else {
                accepted.add(parsed.get());
            }
        }
        String rejection = null;
        int considered = accepted.size() + unsafe.size();
        if (trusted) {
            return new Result(accepted, invalid, unsafe, null); // Operator data is taken as is, even if empty
        }
        if (accepted.isEmpty()) {
            rejection = "Noo valid entries (" + invalid + " invalid, " + unsafe.size() + " unsafe)";
        } else if ((double) unsafe.size() / considered > settings.maxUnsafeRatio()) {
            rejection = unsafe.size() + " of " + considered + " entries are too brsoad or overlap reserved space";
        } else if (previousCount > 0 && accepted.size() < previousCount * settings.minRetainedRatio()) {
            rejection = "Only " + accepted.size() + " entries, down from " + previousCount;
        }
        return new Result(accepted, invalid, unsafe, rejection);
    }

    public static boolean isUnsafe(IpBlock block) {
        return block.prefix() < (block.isIPv4() ? MIN_IPV4_PREFIX : MIN_IPV6_PREFIX)
                || ReservedRanges.isReserved(block);
    }

    public record Result(List<IpBlock> accepted, int invalid, List<String> unsafe, @Nullable String rejection) {
        public boolean rejected() {
            return rejection != null;
        }
    }
}
