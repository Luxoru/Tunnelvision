package dev.luxoru.tunnelvision.detection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictEngineTest {
    private final VerdictEngine engine = new VerdictEngine(Weights.DEFAULTS, BlockPolicy.DEFAULT);

    private static Detection detection(Category category, Confidence confidence) {
        return new Detection(category.name().toLowerCase(), "Name " + category, category, confidence, "1.2.3.0/24");
    }

    @Test
    void cleanWithoutDetections() {
        Verdict verdict = engine.evaluate("1.2.3.4", List.of());
        assertThat(verdict.risk()).isZero();
        assertThat(verdict.blocked()).isFalse();
        assertThat(verdict.detections()).isEmpty();
    }

    @Test
    void confirmedVpnNamesTheProvider() {
        Verdict verdict = engine.evaluate("1.2.3.4", List.of(detection(Category.VPN, Confidence.CONFIRMED)));
        assertThat(verdict.is(Category.VPN)).isTrue();
        assertThat(verdict.provider()).isEqualTo("Name VPN");
        assertThat(verdict.risk()).isEqualTo(1.0);
        assertThat(verdict.blocked()).isTrue();
    }

    @Test
    void likelyVpnHasNoProvider() {
        Verdict verdict = engine.evaluate("1.2.3.4", List.of(detection(Category.VPN, Confidence.LIKELY)));
        assertThat(verdict.provider()).isNull();
        assertThat(verdict.risk()).isEqualTo(0.75);
        assertThat(verdict.blocked()).isTrue(); // VPN is a blocked category
    }

    @Test
    void hostingAloneIsNotBlockedOrAVpn() {
        Verdict verdict = engine.evaluate("1.2.3.4", List.of(detection(Category.HOSTING, Confidence.CONFIRMED)));
        assertThat(verdict.is(Category.HOSTING)).isTrue();
        assertThat(verdict.is(Category.VPN)).isFalse();
        assertThat(verdict.blocked()).isFalse();
        assertThat(verdict.risk()).isEqualTo(0.5);
    }

    @Test
    void signalsDoNotStack() {
        Verdict verdict = engine.evaluate("1.2.3.4", List.of(
            detection(Category.HOSTING, Confidence.CONFIRMED), detection(Category.HOSTING, Confidence.LIKELY)));
        assertThat(verdict.risk()).isEqualTo(0.5);
    }

    @Test
    void relaySuppressesHostingAndLikelyVpn() {
        Verdict verdict = engine.evaluate("1.2.3.4", List.of(detection(Category.RELAY, Confidence.CONFIRMED),
            detection(Category.HOSTING, Confidence.CONFIRMED), detection(Category.VPN, Confidence.LIKELY)));
        assertThat(verdict.is(Category.RELAY)).isTrue();
        assertThat(verdict.is(Category.HOSTING)).isFalse();
        assertThat(verdict.is(Category.VPN)).isFalse();
        assertThat(verdict.blocked()).isFalse();
        assertThat(verdict.risk()).isEqualTo(0.1);
        assertThat(verdict.detections()).extracting(Detection::category).containsExactly(Category.RELAY);
    }

    @Test
    void relayDoesNotSuppressConfirmedVpn() {
        Verdict verdict = engine.evaluate("1.2.3.4", List.of(detection(Category.RELAY, Confidence.CONFIRMED),
            detection(Category.VPN, Confidence.CONFIRMED)));
        assertThat(verdict.is(Category.VPN)).isTrue();
        assertThat(verdict.blocked()).isTrue();
    }

    @Test
    void riskThresholdBlocks() {
        VerdictEngine strict = new VerdictEngine(Weights.DEFAULTS, new BlockPolicy(Set.of(), 0.5));
        assertThat(strict.evaluate("1.2.3.4", List.of(detection(Category.HOSTING, Confidence.LIKELY))).blocked()).isTrue();
        assertThat(strict.evaluate("1.2.3.4", List.of(detection(Category.RELAY, Confidence.CONFIRMED))).blocked()).isFalse();
    }
}
