package dev.luxoru.tunnelvision.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An immutable set of IPv4 and IPv6 blocks with fast containment lookups.
 * <p>
 * Blocks nested inside other blocks are dropped, and lookups are a binary
 * search over the remaining disjoint blocks, so they take well under a
 * microsecond regardless of size.
 * </p>
 */
public final class IpRangeIndex {
    private static final IpRangeIndex EMPTY = new IpRangeIndex(new IpBlock[0]);

    private static final Comparator<IpBlock> ORDER = (a, b) -> {
        int start = IpBlock.compare(a.startHi(), a.startLo(), b.startHi(), b.startLo());
        return start != 0 ? start : IpBlock.compare(b.endHi(), b.endLo(), a.endHi(), a.endLo()); // Widest first
    };

    private final long[] startHi, startLo, endHi, endLo;
    private final IpBlock[] blocks;

    private IpRangeIndex(IpBlock[] blocks) {
        this.blocks = blocks;
        int size = blocks.length;
        startHi = new long[size];
        startLo = new long[size];
        endHi = new long[size];
        endLo = new long[size];
        for (int i = 0; i < size; i++) {
            startHi[i] = blocks[i].startHi();
            startLo[i] = blocks[i].startLo();
            endHi[i] = blocks[i].endHi();
            endLo[i] = blocks[i].endLo();
        }
    }

    /**
     * An empty index.
     *
     * @return the empty index
     */
    public static IpRangeIndex empty() {
        return EMPTY;
    }

    /**
     * Build an index from blocks or addresses in text form, invalid entries are skipped.
     *
     * @param entries the entries
     * @return the index
     */
    public static IpRangeIndex parse(Collection<String> entries) {
        List<IpBlock> blocks = new ArrayList<>(entries.size());
        for (String entry : entries) {
            IpBlock.parse(entry).ifPresent(blocks::add);
        }
        return of(blocks);
    }

    /**
     * Build an index from the given blocks.
     *
     * @param blocks the blocks
     * @return the index
     */
    public static IpRangeIndex of(Collection<IpBlock> blocks) {
        if (blocks.isEmpty()) {
            return EMPTY;
        }
        IpBlock[] sorted = blocks.toArray(IpBlock[]::new);
        Arrays.sort(sorted, ORDER);
        List<IpBlock> disjoint = new ArrayList<>(sorted.length);
        IpBlock last = null;
        for (IpBlock block : sorted) {
            // CIDR blocks are either disjoint or nested, so anything that
            // starts inside the previous block is entirely inside it
            if (last != null && IpBlock.compare(block.startHi(), block.startLo(), last.endHi(), last.endLo()) <= 0) {
                continue;
            }
            disjoint.add(block);
            last = block;
        }
        return new IpRangeIndex(disjoint.toArray(IpBlock[]::new));
    }

    /**
     * Find the block containing the given address or block.
     *
     * @param address the address
     * @return the containing block, empty if none
     */
    public Optional<IpBlock> find(IpBlock address) {
        int index = floor(address.startHi(), address.startLo());
        if (index < 0 || IpBlock.compare(endHi[index], endLo[index], address.endHi(), address.endLo()) < 0) {
            return Optional.empty();
        }
        return Optional.of(blocks[index]);
    }

    /**
     * Whether the given address or block is entirely inside a block in this index.
     *
     * @param address the address
     * @return true if contained
     */
    public boolean contains(IpBlock address) {
        return find(address).isPresent();
    }

    /**
     * Whether any part of the given block overlaps a block in this index.
     *
     * @param block the block
     * @return true if they overlap
     */
    public boolean overlaps(IpBlock block) {
        int index = floor(block.endHi(), block.endLo());
        return index >= 0 && IpBlock.compare(endHi[index], endLo[index], block.startHi(), block.startLo()) >= 0;
    }

    /**
     * The amount of disjoint blocks in this index.
     *
     * @return the size
     */
    public int size() {
        return blocks.length;
    }

    /**
     * The disjoint blocks in this index, in ascending order.
     *
     * @return the blocks
     */
    public List<IpBlock> blocks() {
        return Collections.unmodifiableList(Arrays.asList(blocks));
    }

    /**
     * The index of the last block starting at or before the given value, -1 if none.
     */
    private int floor(long hi, long lo) {
        int low = 0, high = blocks.length - 1, result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (IpBlock.compare(startHi[mid], startLo[mid], hi, lo) <= 0) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }
}
