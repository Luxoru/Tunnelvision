package dev.luxoru.tunnelvision.cache;

import dev.luxoru.tunnelvision.detection.Category;
import dev.luxoru.tunnelvision.detection.Confidence;
import dev.luxoru.tunnelvision.detection.Detection;
import dev.luxoru.tunnelvision.detection.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerdictCodecTest {
    private static final Detection DETECTION =
        new Detection("nordvpn", "NordVPN", Category.VPN, Confidence.CONFIRMED, "2001:db8::/32");

    @Test
    void roundTrips() {
        Verdict verdict = new Verdict("2001:db8::1", 0.75, true, Verdict.Status.CHECKED,
            Set.of(Category.VPN, Category.HOSTING), "NordVPN", List.of(DETECTION));
        assertThat(VerdictCodec.decode(VerdictCodec.encode(verdict))).hasValue(verdict);

        Verdict reserved = Verdict.reserved("10.0.0.1");
        assertThat(VerdictCodec.decode(VerdictCodec.encode(reserved))).hasValue(reserved);
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void roundTripsEachCategory(Category category) {
        Verdict verdict = new Verdict("1.2.3.4", 0.5, false, Verdict.Status.CHECKED, Set.of(category), null, List.of());
        assertThat(VerdictCodec.decode(VerdictCodec.encode(verdict))).hasValue(verdict);
    }

    @ParameterizedTest
    @EnumSource(Verdict.Status.class)
    void roundTripsEachStatus(Verdict.Status status) {
        Verdict verdict = new Verdict("1.2.3.4", 0, false, status, Set.of(), null, List.of());
        assertThat(VerdictCodec.decode(VerdictCodec.encode(verdict))).hasValue(verdict);
    }

    @Test
    void roundTripsMoreDetectionsThanASignedShortHolds() {
        Verdict verdict = new Verdict("1.2.3.4", 1, true, Verdict.Status.CHECKED, Set.of(Category.VPN), null,
            Collections.nCopies(40_000, DETECTION));
        assertThat(VerdictCodec.decode(VerdictCodec.encode(verdict))).hasValue(verdict);
    }

    @Test
    void refusesToEncodeTooManyDetections() {
        Verdict verdict = new Verdict("1.2.3.4", 1, true, Verdict.Status.CHECKED, Set.of(Category.VPN), null,
            Collections.nCopies(65_536, DETECTION));
        assertThatThrownBy(() -> VerdictCodec.encode(verdict)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToEncodeStringsOverTheUtfLimit() {
        Detection huge = new Detection("huge", "x".repeat(70_000), Category.VPN, Confidence.LIKELY, "1.2.3.4");
        Verdict verdict = new Verdict("1.2.3.4", 1, true, Verdict.Status.CHECKED, Set.of(Category.VPN), null,
            List.of(huge));
        assertThatThrownBy(() -> VerdictCodec.encode(verdict)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void unknownPayloadsDecodeToEmpty() {
        assertThat(VerdictCodec.decode(new byte[] { 99, 1, 2 })).isEmpty();
        assertThat(VerdictCodec.decode(new byte[0])).isEmpty();
    }

    @Test
    void truncatedOrPaddedPayloadsDecodeToEmpty() {
        byte[] encoded = VerdictCodec.encode(Verdict.reserved("10.0.0.1"));
        assertThat(VerdictCodec.decode(Arrays.copyOf(encoded, encoded.length - 1))).isEmpty();
        assertThat(VerdictCodec.decode(Arrays.copyOf(encoded, encoded.length + 1))).isEmpty();
    }
}
