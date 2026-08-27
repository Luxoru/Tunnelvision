package dev.luxoru.tunnelvision.source.registry;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The state of a source.
 *
 * @param id the source id
 * @param name the display name
 * @param category what a match means
 * @param confidence how certain a match is
 * @param state whether the source is in use
 * @param entries how many entries were accepted on the last good refresh
 * @param blocks how many disjoint blocks those entries merged into
 * @param refreshedAt when the data in use was fetched, null if never
 * @param lastError why the last refresh failed or was rejected, null if it succeeded
 */
public record SourceStatus(String id, String name, Category category, Confidence confidence, State state, int entries,
                           int blocks, @Nullable Instant refreshedAt, @Nullable String lastError) {
    public enum State {
        PENDING,
        ACTIVE,
        STALE,
        FAILED
    }
}
