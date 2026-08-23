package dev.luxoru.tunnelvision.source;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.source.list.PlainListSource;

import java.time.Duration;
import java.util.List;

/**
 * A list of addresses or CIDR blocks that mean something about an address, such as a VPN provider's servers.
 * <p>
 * Implement this to add your own source. {@link PlainListSource} covers the common one-entry-per-line format.
 * </p>
 */
public interface DetectionSource {
    /**
     * The unique id of this source, lowercase letters, digits and dashes.
     *
     * @return the id
     */
    String id();

    /**
     * The display name of this source.
     *
     * @return the name
     */
    String name();

    /**
     * What a match from this source means.
     *
     * @return the category
     */
    Category category();

    /**
     * How certain a match from this source is.
     *
     * @return the confidence
     */
    Confidence confidence();

    /**
     * How often this source is refreshed, {@link Duration#ZERO} for a
     * source that is loaded once and never goes stale.
     *
     * @return the refresh interval
     */
    Duration refreshInterval();

    /**
     * Fetch the current entries, CIDR blocks or single addresses.
     *
     * @param context what the source can use to fetch its data
     * @return the entries
     * @throws Exception if the fetch fails, the previous data is kept
     */
    List<String> fetch(SourceContext context) throws Exception;

    /**
     * Whether this source's data is curated by the operator (e.g. a static denylist). Unsafe
     * entries are still dropped, but a trusted refresh is never rejected as a whole.
     *
     * @return true if trusted
     */
    default boolean trusted() {
        return false;
    }
}
