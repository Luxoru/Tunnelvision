package dev.luxoru.tunnelvision.detection;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeightsTest {
    private static Detection detection(Category category, Confidence confidence) {
        return new Detection("source", "Source", category, confidence, "1.2.3.0/24");
    }

    @Test
    void likelyMatchesUseTheirOverrideOrFallBackToConfirmed() {
        assertThat(Weights.DEFAULTS.of(detection(Category.VPN, Confidence.CONFIRMED))).isEqualTo(1.0);
        assertThat(Weights.DEFAULTS.of(detection(Category.VPN, Confidence.LIKELY))).isEqualTo(0.75);
        assertThat(Weights.DEFAULTS.of(detection(Category.HOSTING, Confidence.LIKELY))).isEqualTo(0.5);
    }

    @Test
    void everyCategoryNeedsAWeight() {
        Map<Category, Double> missingRelay = new EnumMap<>(Weights.DEFAULTS.confirmed());
        missingRelay.remove(Category.RELAY);
        assertThatThrownBy(() -> new Weights(missingRelay, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RELAY");
    }

    @Test
    void weightsMustBeBetweenZeroAndOne() {
        assertThatThrownBy(() -> Weights.DEFAULTS.with(Map.of(Category.TOR, 1.5), Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Weights.DEFAULTS.with(Map.of(), Map.of(Category.VPN, -0.1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withReplacesOnlyTheGivenWeights() {
        Weights weights = Weights.DEFAULTS.with(Map.of(Category.HOSTING, 0.3), Map.of(Category.PROXY, 0.6));
        assertThat(weights.of(detection(Category.HOSTING, Confidence.CONFIRMED))).isEqualTo(0.3);
        assertThat(weights.of(detection(Category.PROXY, Confidence.LIKELY))).isEqualTo(0.6);
        assertThat(weights.of(detection(Category.PROXY, Confidence.CONFIRMED))).isEqualTo(0.9);
        assertThat(weights.of(detection(Category.VPN, Confidence.LIKELY))).isEqualTo(0.75);
    }
}
