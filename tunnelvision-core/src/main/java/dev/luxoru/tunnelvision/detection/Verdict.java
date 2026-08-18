package dev.luxoru.tunnelvision.detection;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The verdict for an address.
 *
 * @param ip the address
 * @param risk the risk score, between 0 and 1
 * @param blocked whether the {@link BlockPolicy} says to block this address
 * @param status whether the address was checked, or skipped as allowlisted or reserved
 * @param categories every category that matched, e.g. {@link Category#VPN} and {@link Category#HOSTING}
 * @param provider the VPN provider, when its own server list matched
 * @param detections the detections that counted towards this verdict
 */
public record Verdict(String ip, double risk, boolean blocked, Status status, Set<Category> categories,
                      @Nullable String provider, List<Detection> detections) {

    public enum Status {
        CHECKED,
        ALLOWLISTED,
        RESERVED
    }

    public boolean is(Category category) {
        return categories.contains(category);
    }

    public static Verdict allowlisted(String ip) {
        return new Verdict(ip, 0, false, Status.ALLOWLISTED, Set.of(), null, List.of());
    }

    public static Verdict reserved(String ip) {
        return new Verdict(ip, 0, false, Status.RESERVED, Set.of(), null, List.of());
    }
}
