package dev.luxoru.tunnelvision.detection;

import java.util.EnumMap;
import java.util.Map;

public record Weights(Map<Category, Double> confirmed, Map<Category, Double> likely) {
    public static final Weights DEFAULTS = new Weights(
        Map.of(Category.VPN, 1.0, Category.TOR, 1.0, Category.PROXY, 0.9, Category.ABUSE, 0.9,
            Category.HOSTING, 0.5, Category.RELAY, 0.1),
        Map.of(Category.VPN, 0.75));

    public Weights {
        confirmed = Map.copyOf(confirmed);
        likely = Map.copyOf(likely);
        // Every category needs a weight, so a new one can't silently count as no risk
        for (Category category : Category.values()) {
            if (!confirmed.containsKey(category)) {
                throw new IllegalArgumentException("No weight for " + category);
            }
        }
        checkRange(confirmed);
        checkRange(likely);
    }

    private static void checkRange(Map<Category, Double> weights) {
        weights.forEach((category, weight) -> {
            if (weight < 0 || weight > 1) {
                throw new IllegalArgumentException("Weight for " + category + " must be between 0 and 1: " + weight);
            }
        });
    }

    public Weights with(Map<Category, Double> confirmed, Map<Category, Double> likely) {
        Map<Category, Double> mergedConfirmed = new EnumMap<>(this.confirmed);
        mergedConfirmed.putAll(confirmed);
        Map<Category, Double> mergedLikely = new EnumMap<>(Category.class);
        mergedLikely.putAll(this.likely);
        mergedLikely.putAll(likely);
        return new Weights(mergedConfirmed, mergedLikely);
    }

    public double of(Detection detection) {
        if (detection.confidence() == Confidence.LIKELY) {
            Double weight = likely.get(detection.category());
            if (weight != null) {
                return weight;
            }
        }
        return confirmed.get(detection.category());
    }
}
