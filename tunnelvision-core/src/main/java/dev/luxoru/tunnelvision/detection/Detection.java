package dev.luxoru.tunnelvision.detection;

import java.util.Objects;

/**
 * A single signal that matched an address.
 *
 * @param source the id of the source that matched
 * @param name the display name of the source
 * @param category what the match says about the address
 * @param confidence how certain the match is
 * @param match what matched, a CIDR block or address
 */
public record Detection(String source, String name, Category category, Confidence confidence, String match) {
}
