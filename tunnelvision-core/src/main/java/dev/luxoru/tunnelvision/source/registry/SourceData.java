package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.net.IpRangeIndex;
import dev.luxoru.tunnelvision.source.guard.SourceGuard;

import java.time.Instant;

/**
 * A source's data at one point in time.
 *
 * @param index the merged blocks, for lookups
 * @param entries how many entries were accepted, before merging
 * @param refreshedAt when the data was fetched
 */
public record SourceData(IpRangeIndex index, int entries, Instant refreshedAt) {
    public static SourceData of(SourceGuard.Result result, Instant refreshedAt) {
        return new SourceData(IpRangeIndex.of(result.accepted()), result.accepted().size(), refreshedAt);
    }
}
