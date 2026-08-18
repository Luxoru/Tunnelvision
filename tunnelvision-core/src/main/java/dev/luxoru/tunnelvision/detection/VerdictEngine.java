package dev.luxoru.tunnelvision.detection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the detections for an address into a verdict.
 * <p>
 * Rules that keep ordinary users from being flagged:
 * <ul>
 *     <li>Only VPN sources add {@link Category#VPN}. Hosting, abuse and relay matches never do.</li>
 *     <li>A relay match (e.g. iCloud Private Relay) suppresses hosting and likely-VPN
 *     matches, since relays egress from CDN networks that are also on hosting lists.</li>
 *     <li>The risk is the strongest single signal, weaker signals don't stack.</li>
 * </ul>
 * </p>
 */
public final class VerdictEngine {
    private final Weights weights;
    private final BlockPolicy policy;

    public VerdictEngine(Weights weights, BlockPolicy policy) {
        this.weights = weights;
        this.policy = policy;
    }

    public Verdict evaluate(String ip, List<Detection> detections) {
        boolean relay = detections.stream().anyMatch(detection -> detection.category() == Category.RELAY);
        List<Detection> kept = new ArrayList<>(detections.size());
        Set<Category> categories = EnumSet.noneOf(Category.class);
        double risk = 0;
        String provider = null;
        for (Detection detection : detections) {
            if (relay && isSuppressedByRelay(detection)) {
                continue;
            }
            kept.add(detection);
            categories.add(detection.category());
            risk = Math.max(risk, weights.of(detection));
            if (provider == null
                    && detection.category() == Category.VPN
                    && detection.confidence() == Confidence.CONFIRMED) {
                provider = detection.name();
            }
        }
        risk = Math.round(risk * 100) / 100.0;
        boolean blocked = risk >= policy.riskThreshold()
                || categories.stream()
                             .anyMatch(policy.categories()::contains);
        return new Verdict(ip, risk, blocked, Verdict.Status.CHECKED, categories, provider, kept);
    }

    private static boolean isSuppressedByRelay(Detection detection) {
        return detection.category() == Category.HOSTING
               || (detection.category() == Category.VPN
                && detection.confidence() == Confidence.LIKELY);
    }
}
